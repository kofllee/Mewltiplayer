package dev.mewltiplayer.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mewltiplayer.core.MewltiplayerCore;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

@Mod("mewltiplayer")
public final class MewltiplayerMod {
    private MinecraftServer runningServer;

    public MewltiplayerMod(IEventBus modBus, Dist dist, net.neoforged.fml.ModContainer container) {
        MewltiplayerCore.initialize();
        MewDedicatedCommands.register();
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopping);
        if (dist == Dist.CLIENT) {
            MewltiplayerClient.register(container);
        }
    }

    private void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        runningServer = server;
        if (!MewServerCompat.dedicated(server) || !dedicatedEnabled()) return;
        MewRuntime.INSTANCE.share(MewServerCompat.port(server), true).whenComplete((code, error) -> {
            if (error != null) {
                System.err.println("[Mewltiplayer] Dedicated server sharing failed: " + reason(error));
            } else {
                System.out.println("[Mewltiplayer] Share code: " + code
                        + " (saved to config/mewltiplayer/share-code.txt)");
            }
        });
    }

    private void stopping(ServerStoppingEvent event) {
        if (event.getServer() != runningServer) return;
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
            JsonObject config = JsonParser.parseString(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
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