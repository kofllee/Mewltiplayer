package dev.mewltiplayer.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean worldAvailable() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    public static void connectToInvite(Screen parent, String code) {
        Minecraft client = Minecraft.getInstance();
        ServerData data = new ServerData("Mewltiplayer", code, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, client, new ServerAddress("127.0.0.1", 25565),
                data, false, new TransferState(java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), false));
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
            try {
                for (int attempt = 0; attempt < 4; attempt++) {
                    int port;
                    try (ServerSocket socket = new ServerSocket(0)) {
                        port = socket.getLocalPort();
                    }
                    if (server.publishServer(MinecraftServer.MultiplayerScope.LAN, port)) {
                        result.complete(true);
                        return;
                    }
                }
                result.complete(false);
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
        if (!MewRuntime.INSTANCE.settings().notifications) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Mewltiplayer] " + text));
        }
    }
}
