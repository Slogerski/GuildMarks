package pl.guildmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

final class MojangSessionProof {
    private MojangSessionProof() {}

    static void submitDirectlyToMojang(MinecraftClient client, Session session, String serverId) throws Exception {
        if (session.getUuidOrNull() == null) throw new IllegalStateException("Minecraft profile UUID is unavailable");
        client.getSessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
    }
}
