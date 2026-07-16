package pl.guildmark;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static pl.guildmark.GuildMarkI18n.tr;

public final class GuildAutoImporter {
    public record Summary(int guilds, int images) {}
    private record ImportedMark(GuildData.Guild guild, BufferedImage image) {}
    private record PreparedImport(GuildData data, List<ImportedMark> marks) {}
    private record InstalledMark(Path target, Path backup) {}

    private static final int MAX_JSON_BYTES = 1_000_000;
    private static final int MAX_IMAGE_BYTES = 5_000_000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GuildMark-AutoImport");
        thread.setDaemon(true);
        return thread;
    });

    private GuildAutoImporter() {}

    public static String normalizeUrl(String raw) {
        URI uri = URI.create(raw == null ? "" : raw.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException(tr("an HTTPS JSON address is required", "wymagany jest adres HTTPS do JSON"));
        return uri.normalize().toString();
    }

    public static void start(String raw, Consumer<Summary> success, Consumer<Throwable> failure) {
        final String url;
        try {
            url = normalizeUrl(raw);
        } catch (Throwable error) {
            failure.accept(error);
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            failure.accept(new IllegalStateException(tr("a guild import is already running", "import gildii już trwa")));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                try { return prepare(url); }
                catch (Throwable error) { throw new java.util.concurrent.CompletionException(error); }
            }, DOWNLOADS)
            .thenAccept(prepared -> MinecraftClient.getInstance().execute(() -> {
                try {
                    Summary summary = commit(prepared);
                    RUNNING.set(false);
                    success.accept(summary);
                } catch (Throwable error) {
                    RUNNING.set(false);
                    failure.accept(error);
                }
            }))
            .exceptionally(error -> {
                MinecraftClient.getInstance().execute(() -> {
                    RUNNING.set(false);
                    Throwable cause = rootCause(error);
                    GuildMarkClient.LOGGER.warn("Guild import failed for {}; keeping the current database", url, cause);
                    failure.accept(cause);
                });
                return null;
            });
    }

    private static PreparedImport prepare(String url) throws Exception {
        byte[] json = download(url, "application/json", MAX_JSON_BYTES, 15_000, "JSON");
        GuildData incoming = GuildMarkClient.STORE.parseJson(new String(json, java.nio.charset.StandardCharsets.UTF_8));
        List<ImportedMark> downloads = new ArrayList<>();
        for (GuildData.Guild guild : incoming.guilds) {
            if (guild.markUrl == null || guild.markUrl.isBlank()) continue;
            String markUrl = normalizeUrl(guild.markUrl);
            byte[] bytes = download(markUrl, "image/webp,image/png,image/jpeg,image/*", MAX_IMAGE_BYTES, 30_000, guild.name);
            BufferedImage image = readImage(bytes);
            validateImage(image);
            downloads.add(new ImportedMark(guild, image));
        }
        return new PreparedImport(incoming, List.copyOf(downloads));
    }

    private static Summary commit(PreparedImport prepared) throws Exception {
        Path root = markRoot();
        Files.createDirectories(root);
        List<Path> staged = new ArrayList<>();
        List<InstalledMark> installed = new ArrayList<>();
        Set<String> uniqueFiles = new HashSet<>();
        Set<String> oldFiles = collectMarkFiles(GuildMarkClient.STORE.data());
        boolean dataCommitted = false;
        try {
            for (ImportedMark imported : prepared.marks()) {
                String file = markFileName(imported.guild().name);
                if (!uniqueFiles.add(file.toLowerCase(Locale.ROOT)))
                    throw new IllegalStateException(tr("Guild names create the same mark filename", "Nazwy gildii tworzą tę samą nazwę pliku znaku"));
                Path temp = Files.createTempFile(root, ".guildmark-auto-import-", ".png");
                staged.add(temp);
                if (!ImageIO.write(imported.image(), "PNG", temp.toFile()))
                    throw new IllegalStateException(tr("PNG encoder unavailable", "brak kodera PNG"));
                imported.guild().markFile = file;
                imported.guild().markPath = "GuildMark/Guilds/" + file;
            }

            for (int i = 0; i < prepared.marks().size(); i++) {
                Path target = resolveMarkFile(prepared.marks().get(i).guild().markFile);
                Path backup = null;
                if (Files.exists(target)) {
                    backup = Files.createTempFile(root, ".guildmark-backup-", ".png");
                    moveReplacing(target, backup);
                }
                installed.add(new InstalledMark(target, backup));
                moveReplacing(staged.get(i), target);
            }

            GuildMarkClient.STORE.importData(prepared.data());
            dataCommitted = true;
        } catch (Throwable error) {
            rollback(installed);
            if (error instanceof Exception exception) throw exception;
            throw new IllegalStateException(error);
        } finally {
            for (Path temp : staged) deleteQuietly(temp);
        }

        if (dataCommitted) {
            for (InstalledMark mark : installed) deleteQuietly(mark.backup());
            Set<String> currentFiles = collectMarkFiles(prepared.data());
            oldFiles.stream().filter(file -> !currentFiles.contains(file)).forEach(file -> {
                try { Files.deleteIfExists(resolveMarkFile(file)); } catch (Exception ignored) { }
            });
            GuildMarkTextures.invalidateAll();
        }
        return new Summary(prepared.data().guilds.size(), prepared.marks().size());
    }

    private static void rollback(List<InstalledMark> installed) {
        for (int i = installed.size() - 1; i >= 0; i--) {
            InstalledMark mark = installed.get(i);
            deleteQuietly(mark.target());
            if (mark.backup() != null && Files.exists(mark.backup())) {
                try { moveReplacing(mark.backup(), mark.target()); } catch (Exception ignored) { }
            }
        }
    }

    private static Set<String> collectMarkFiles(GuildData data) {
        Set<String> files = new HashSet<>();
        if (data != null && data.guilds != null) for (GuildData.Guild guild : data.guilds)
            if (guild != null && guild.markFile != null && !guild.markFile.isBlank()) files.add(guild.markFile);
        return files;
    }

    private static byte[] download(String url, String accept, int maximumBytes, int timeoutMillis, String name) throws Exception {
        try {
            return downloadOnce(url, accept, maximumBytes, timeoutMillis, name);
        } catch (IOException firstFailure) {
            return downloadOnce(url, accept, maximumBytes, timeoutMillis, name);
        }
    }

    private static byte[] downloadOnce(String url, String accept, int maximumBytes, int timeoutMillis, String name) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Accept", accept)
            .header("User-Agent", "GuildMark/1.0.2 Minecraft/1.21.8")
            .GET()
            .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2)
            throw new IllegalStateException(name + ": HTTP " + response.statusCode());
        byte[] bytes = response.body();
        if (bytes.length > maximumBytes)
            throw new IllegalStateException(name + tr(": file is too large", ": plik jest za duży"));
        return bytes;
    }

    private static BufferedImage readImage(byte[] bytes) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException(tr("response is not a PNG, JPG, or WEBP image", "odpowiedź nie jest obrazem PNG, JPG ani WEBP"));
            return image;
        }
    }

    private static void validateImage(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() > 4096 || image.getHeight() > 4096 || (long) image.getWidth() * image.getHeight() > 16_000_000L)
            throw new IllegalArgumentException(tr("maximum image size is 4096×4096 px", "dozwolony obraz ma maksymalnie 4096×4096 px"));
    }

    private static String markFileName(String guildName) {
        String safe = guildName.replaceAll("[\\\\/:*?\"<>|]", "_").strip().replaceAll("[. ]+$", "");
        return (safe.isBlank() ? "guild" : safe) + ".png";
    }

    private static Path markRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("GuildMark/Guilds").toAbsolutePath().normalize();
    }

    private static Path resolveMarkFile(String file) {
        Path root = markRoot();
        if (file == null || file.isBlank())
            throw new IllegalArgumentException(tr("unsafe mark path", "niebezpieczna ścieżka znaku"));
        Path resolved = root.resolve(file).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root))
            throw new IllegalArgumentException(tr("unsafe mark path", "niebezpieczna ścieżka znaku"));
        return resolved;
    }

    private static void moveReplacing(Path source, Path target) throws Exception {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
    }

    public static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}
