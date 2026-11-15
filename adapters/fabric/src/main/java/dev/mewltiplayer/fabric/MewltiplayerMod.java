package dev.mewltiplayer.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mewltiplayer.core.MewltiplayerCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

public final class MewltiplayerMod implements ModInitializer {
    private MinecraftServer runningServer;

    @Override
    public void onInitialize() {
        MewltiplayerCore.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stopping);
    }

    private void started(MinecraftServer server) {
        runningServer = server;
        if (!MewServerCompat.dedicated(server) || !dedicatedEnabled()) {
            return;
        }

        int port = MewServerCompat.port(server);
        MewRuntime.INSTANCE.share(port, true).whenComplete((code, error) -> {
            if (error != null) {
                System.err.println("[Mewltiplayer] Dedicated server sharing failed: " + reason(error));
            } else {
                System.out.println("[Mewltiplayer] Tailcat invitation saved to config/mewltiplayer/invite.txt");
            }
        });
    }

    private void stopping(MinecraftServer server) {
        if (server != runningServer) {
            return;
        }
        MewRuntime.INSTANCE.stopShare();
        runningServer = null;
    }

    private boolean dedicatedEnabled() {
        Path file = MewRuntime.INSTANCE.configDirectory().resolve("server.json");
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, ("{\n  \"enabled\": true\n}\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException error) {
                System.err.println("[Mewltiplayer] Could not create server configuration: " + error.getMessage());
            }
            return true;
        }
        try {
            JsonObject config = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
            return !config.has("enabled") || config.get("enabled").getAsBoolean();
        } catch (Exception error) {
            System.err.println("[Mewltiplayer] Invalid server configuration: " + error.getMessage());
            return false;
        }
    }

    private static String reason(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
