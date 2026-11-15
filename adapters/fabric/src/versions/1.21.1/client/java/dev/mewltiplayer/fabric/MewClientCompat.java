package dev.mewltiplayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean published() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server != null && server.isRemote();
    }

    public static int worldPort() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server == null ? 0 : server.getServerPort();
    }

    public static CompletableFuture<Boolean> publishWorld() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> {
            if (server.isRemote()) {
                result.complete(true);
                return;
            }
            try (ServerSocket socket = new ServerSocket(0)) {
                result.complete(server.openToLan(server.getDefaultGameMode(), false, socket.getLocalPort()));
            } catch (Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public static void execute(Runnable task) {
        MinecraftClient.getInstance().execute(task);
    }

    public static void copyInvite(String code) {
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        notice("Tailcat invite copied to clipboard");
    }

    public static void notice(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Mewltiplayer] " + text), false);
        }
    }
}
