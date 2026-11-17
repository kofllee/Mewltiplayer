package dev.mewltiplayer.neoforge;

import net.minecraft.server.MinecraftServer;

public final class MewServerCompat {
    private MewServerCompat() {
    }

    public static boolean dedicated(MinecraftServer server) {
        return server.isDedicatedServer();
    }

    public static int port(MinecraftServer server) {
        return server.getPort();
    }
}
