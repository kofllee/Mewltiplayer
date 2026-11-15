package dev.mewltiplayer.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class MewWorldSettings {
    private MewWorldSettings() {}

    public static Path folder(Path worldDirectory) {
        return worldDirectory.resolve("mewltiplayer");
    }

    public static Path identity(Path worldDirectory) {
        return folder(worldDirectory).resolve("identity.json");
    }

    public static boolean pinned(Path worldDirectory) {
        return flag(worldDirectory, "pinnedShareCode");
    }

    public static boolean shareViaTailcat(Path worldDirectory) {
        return flag(worldDirectory, "shareViaTailcat");
    }

    public static void setShareViaTailcat(Path worldDirectory, boolean enabled) throws IOException {
        update(worldDirectory, json -> json.addProperty("shareViaTailcat", enabled));
    }

    public static void setPinned(Path worldDirectory, boolean pinned) throws IOException {
        update(worldDirectory, json -> json.addProperty("pinnedShareCode", pinned));
        // Retain existing identities when users switch share code modes.
    }

    public static void resetIdentity(Path worldDirectory) throws IOException {
        if (worldDirectory == null) throw new IOException("Open a singleplayer world first");
        Files.deleteIfExists(identity(worldDirectory));
    }

    private static boolean flag(Path worldDirectory, String key) {
        JsonObject json = read(worldDirectory);
        try {
            return json.has(key) && json.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonObject read(Path worldDirectory) {
        if (worldDirectory == null) return new JsonObject();
        Path file = folder(worldDirectory).resolve("settings.json");
        if (!Files.isRegularFile(file)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static void update(Path worldDirectory, Consumer<JsonObject> change) throws IOException {
        if (worldDirectory == null) throw new IOException("Open a singleplayer world first");
        Path directory = folder(worldDirectory);
        Files.createDirectories(directory);
        Path destination = directory.resolve("settings.json");
        JsonObject json = read(worldDirectory);
        change.accept(json);
        Path temp = Files.createTempFile(directory, ".settings-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
