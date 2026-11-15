package dev.mewltiplayer.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean published() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPublished();
    }

    public static int worldPort() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? 0 : server.getPort();
    }

    public static CompletableFuture<Boolean> publishWorld() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> {
            if (server.isPublished()) {
                result.complete(true);
                return;
            }
            try (ServerSocket socket = new ServerSocket(0)) {
                result.complete(server.publishServer(MinecraftServer.MultiplayerScope.LAN, socket.getLocalPort()));
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    public static void copyInvite(String code) {
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        notice("Tailcat invite copied to clipboard");
    }

    public static void notice(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Mewltiplayer] " + text));
        }
    }
}
