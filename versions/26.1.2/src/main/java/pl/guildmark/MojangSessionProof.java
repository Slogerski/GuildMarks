package pl.guildmark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

final class MojangSessionProof {
    private MojangSessionProof() {}

    static void submitDirectlyToMojang(Minecraft client, User session, String serverId) throws Exception {
        if (session.getProfileId() == null) throw new IllegalStateException("Minecraft profile UUID is unavailable");
        client.services().sessionService().joinServer(session.getProfileId(), session.getAccessToken(), serverId);
    }
}
