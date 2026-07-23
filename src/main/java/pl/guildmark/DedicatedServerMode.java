package pl.guildmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

public final class DedicatedServerMode {
    private static final String MCEXTREME_HOST = "mcextreme.pl";

    private DedicatedServerMode() {}

    public static boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo server = client == null ? null : client.getCurrentServerEntry();
        return server != null && matches(server.address);
    }

    static boolean matches(String address) {
        if (address == null) return false;
        String host = address.strip().toLowerCase(Locale.ROOT);
        int scheme = host.indexOf("://");
        if (scheme >= 0) host = host.substring(scheme + 3);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end > 0 ? host.substring(1, end) : host;
        } else {
            int colon = host.lastIndexOf(':');
            if (colon > 0 && colon == host.indexOf(':')) host = host.substring(0, colon);
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return MCEXTREME_HOST.equals(host) || host.endsWith("." + MCEXTREME_HOST);
    }
}
