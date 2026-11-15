package dev.mewltiplayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean worldAvailable() {
        return MinecraftClient.getInstance().getServer() != null;
    }

    public static String clipboard() {
        return MinecraftClient.getInstance().keyboard.getClipboard();
    }

    public static void connectToInvite(Screen parent, String code) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo data = new ServerInfo("Mewltiplayer", code, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, new ServerAddress("127.0.0.1", 25565),
                data, false, new CookieStorage(java.util.Collections.emptyMap()));
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
            try {
                for (int attempt = 0; attempt < 4; attempt++) {
                    int port;
                    try (ServerSocket socket = new ServerSocket(0)) {
                        port = socket.getLocalPort();
                    }
                    if (server.openToLan(server.getDefaultGameMode(), false, port)) {
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
        MinecraftClient.getInstance().execute(task);
    }

    public static void copyInvite(String code) {
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        notice("Tailcat invite copied to clipboard");
    }

    public static void notice(String text) {
        if (!MewRuntime.INSTANCE.settings().notifications) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Mewltiplayer] " + text), false);
        }
    }
}
