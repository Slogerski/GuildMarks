package pl.guildmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pl.guildmark.GuildMarkI18n.tr;

public final class GuildStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("guildmark/guilds.json");
    private GuildData data = new GuildData();
    private Map<String, GuildData.Guild> guildByPlayer = Map.of();

    public GuildStore() { load(); }
    public GuildData data() { return data; }
    public GuildData.Guild guildForPlayer(String playerName) {
        return playerName == null ? null : guildByPlayer.get(playerName.toLowerCase(Locale.ROOT));
    }
    public String json() { return GSON.toJson(data); }

    public void load() {
        try {
            if (Files.exists(path)) {
                GuildData loaded = GSON.fromJson(Files.readString(path), GuildData.class);
                validate(loaded);
                data = loaded;
                rebuildPlayerIndex();
            }
            else save();
        } catch (IOException | JsonParseException | IllegalArgumentException e) { GuildMarkClient.LOGGER.error("Cannot load {}", path, e); }
    }

    public void importJson(String json) {
        importData(parseJson(json));
    }

    public GuildData parseJson(String json) {
        GuildData incoming = GSON.fromJson(json, GuildData.class);
        validate(incoming);
        return incoming;
    }

    public void importData(GuildData incoming) {
        validate(incoming);
        write(incoming);
        data = incoming;
        rebuildPlayerIndex();
    }

    private void validate(GuildData incoming) {
        if (incoming == null || incoming.guilds == null) throw new IllegalArgumentException(tr("JSON does not contain a guilds list", "JSON nie zawiera listy guilds"));
        incoming.formatVersion = 2;
        Set<String> guildNames = new HashSet<>();
        Set<String> players = new HashSet<>();
        boolean ownGuildSeen = false;
        for (GuildData.Guild guild : incoming.guilds) {
            if (guild == null || guild.name == null || guild.name.isBlank()) throw new IllegalArgumentException(tr("Guild must have a name", "Gildia musi mieć nazwę"));
            if (!guildNames.add(guild.name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException(tr("Duplicate guild: ", "Powtórzona gildia: ") + guild.name);
            if (guild.players == null) guild.players = new java.util.ArrayList<>();
            if (guild.markFile == null) guild.markFile = "";
            if (guild.markUrl == null) guild.markUrl = "";
            if (guild.markPath == null) guild.markPath = "";
            if (guild.markUrl.isBlank() && guild.markFile.regionMatches(true, 0, "https://", 0, 8)) {
                guild.markUrl = guild.markFile.strip();
                guild.markFile = "";
            }
            if (!guild.markUrl.isBlank()) {
                try {
                    java.net.URI markUri = java.net.URI.create(guild.markUrl.strip());
                    if (!"https".equalsIgnoreCase(markUri.getScheme())) throw new IllegalArgumentException();
                    guild.markUrl = markUri.toString();
                } catch (Exception error) {
                    throw new IllegalArgumentException(tr("Guild mark URL must use HTTPS: ", "Link znaku gildii musi używać HTTPS: ") + guild.name);
                }
            }
            if (!guild.markFile.isBlank()) {
                if (guild.markFile.matches(".*[\\\\/:*?\"<>|].*") || guild.markFile.equals(".") || guild.markFile.equals(".."))
                    throw new IllegalArgumentException(tr("Invalid local mark filename: ", "Nieprawidłowa lokalna nazwa znaku: ") + guild.markFile);
                guild.markPath = "GuildMark/Guilds/" + guild.markFile;
            } else guild.markPath = "";
            if (!("own".equals(guild.relation) || "ally".equals(guild.relation) || "foreign".equals(guild.relation))) guild.relation = "foreign";
            if ("own".equals(guild.relation)) {
                if (ownGuildSeen) guild.relation = "foreign";
                else ownGuildSeen = true;
            }
            guild.players.removeIf(n -> n == null || n.isBlank());
            for (String nick : guild.players) if (!players.add(nick.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException(tr("Player belongs to multiple guilds: ", "Gracz jest w kilku gildiach: ") + nick);
        }
    }

    public void save() {
        validate(data);
        write(data);
        rebuildPlayerIndex();
    }

    private void rebuildPlayerIndex() {
        Map<String, GuildData.Guild> index = new HashMap<>();
        for (GuildData.Guild guild : data.guilds)
            for (String player : guild.players) index.put(player.toLowerCase(Locale.ROOT), guild);
        guildByPlayer = Map.copyOf(index);
        GuildRenderLimiter.invalidate();
    }

    private void write(GuildData value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { throw new IllegalStateException(tr("Failed to save ", "Nie udało się zapisać ") + path, e); }
    }
}
