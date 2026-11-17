package dev.mewltiplayer.fabric;

import net.minecraft.server.MinecraftServer;

public final class MewServerCompat {
    private MewServerCompat() {
    }

    public static boolean dedicated(MinecraftServer server) {
        return server.isDedicated();
    }

    public static int port(MinecraftServer server) {
        return server.getServerPort();
    }
}
