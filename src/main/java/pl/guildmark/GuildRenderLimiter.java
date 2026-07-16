package pl.guildmark;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class GuildRenderLimiter {
    private record Candidate(String name, double distanceSquared) {}

    private static Set<String> allowedPlayers = Set.of();
    private static Object currentWorld;
    private static int ticksUntilRefresh;

    private GuildRenderLimiter() {}

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            allowedPlayers = Set.of();
            currentWorld = null;
            ticksUntilRefresh = 0;
            return;
        }
        if (currentWorld == client.world && ticksUntilRefresh-- > 0) return;
        currentWorld = client.world;
        ticksUntilRefresh = 9;
        refresh(client);
    }

    public static boolean shouldRender(String playerName) {
        if (GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.maxRenderedPlayers() == 0) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getName().getString().equalsIgnoreCase(playerName)) return true;
        return playerName != null && allowedPlayers.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public static void invalidate() {
        ticksUntilRefresh = 0;
        currentWorld = null;
    }

    private static void refresh(MinecraftClient client) {
        int limit = GuildMarkClient.SETTINGS.maxRenderedPlayers();
        if (limit == 0) {
            allowedPlayers = Set.of();
            return;
        }
        int renderDistance = GuildMarkClient.SETTINGS.cosmeticRenderDistance();
        double maximumSquared = renderDistance == 0 ? Double.POSITIVE_INFINITY : (double) renderDistance * renderDistance;
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (var player : client.world.getPlayers()) {
            if (player == client.player) continue;
            String name = player.getName().getString();
            if (GuildMarkClient.STORE.guildForPlayer(name) == null) continue;
            double distanceSquared = player.squaredDistanceTo(client.player);
            if (distanceSquared <= maximumSquared) candidates.add(new Candidate(name.toLowerCase(Locale.ROOT), distanceSquared));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
        HashSet<String> selected = new HashSet<>();
        for (int i = 0; i < Math.min(limit, candidates.size()); i++) selected.add(candidates.get(i).name());
        allowedPlayers = Set.copyOf(selected);
    }
}
