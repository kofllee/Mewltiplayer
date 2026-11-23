package dev.mewltiplayer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MewSettings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String DEFAULT_MAP = "https://tailcat.dev/derpmap.json";
    public static final String NEW = "new";
    public static final String WORLD = "world";
    public static final String GLOBAL = "global";
    public static final String AUTO = "auto";
    public static final String MAP = "map";
    public static final String URL = "url";

    public String shareCodeMode = NEW;
    public boolean showShareCodeInChat = true;
    public String derpMode = AUTO;
    public String derpMapJson = "";
    public String derpMapUrl = "";
    public int regionId;

    public static MewSettings load(Path file) {
        if (!Files.isRegularFile(file)) return new MewSettings();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement document = JsonParser.parseReader(reader);
            MewSettings result = JSON.fromJson(document, MewSettings.class);
            if (result == null) throw new IOException("Empty settings file");
            // Older configurations only stored derpMapUrl; infer their old mode.
            if (!document.getAsJsonObject().has("derpMode")) {
                result.derpMode = result.derpMapUrl == null || result.derpMapUrl.isEmpty() ? AUTO : URL;
            }
            if (!document.getAsJsonObject().has("shareCodeMode")) result.shareCodeMode = NEW;
            if (!document.getAsJsonObject().has("showShareCodeInChat")) result.showShareCodeInChat = true;
            result.validate();
            return result;
        } catch (Exception error) {
            System.err.println("[Mewltiplayer] Invalid settings; using defaults: " + error.getMessage());
            return new MewSettings();
        }
    }

    public void save(Path file) throws IOException {
        validate();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".settings-", ".json");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                JSON.toJson(this, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String activeMapUrl() {
        return URL.equals(derpMode) ? derpMapUrl : "";
    }

    public String activeMapJson() {
        return MAP.equals(derpMode) ? derpMapJson : "";
    }

    public void validate() {
        if (!NEW.equals(shareCodeMode) && !WORLD.equals(shareCodeMode) && !GLOBAL.equals(shareCodeMode)) {
            throw new IllegalArgumentException("Invalid share code mode");
        }
        if (!AUTO.equals(derpMode) && !MAP.equals(derpMode) && !URL.equals(derpMode)) {
            throw new IllegalArgumentException("Invalid DERP mode");
        }
        if (regionId < 0 || regionId > 65535) throw new IllegalArgumentException("Invalid DERP region");
        if (derpMapUrl == null) derpMapUrl = "";
        if (derpMapJson == null) derpMapJson = "";
        if (derpMapUrl.length() > 1024) throw new IllegalArgumentException("DERP map URL is too long");
        if (derpMapJson.length() > 262144) throw new IllegalArgumentException("DERP map is too large");
        if (URL.equals(derpMode) && derpMapUrl.isEmpty()) {
            throw new IllegalArgumentException("Enter a DERP map URL");
        }
        if (URL.equals(derpMode) && !derpMapUrl.isEmpty()) {
            URI uri = URI.create(derpMapUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Custom DERP map requires an HTTPS URL");
            }
        }
        if (MAP.equals(derpMode) && derpMapJson.isEmpty()) {
            throw new IllegalArgumentException("Paste a DERP map first");
        }
        if (MAP.equals(derpMode) && !derpMapJson.isEmpty()) DerpRegions.parse(derpMapJson);
    }

    public MewSettings copy() {
        MewSettings result = new MewSettings();
        result.shareCodeMode = shareCodeMode;
        result.showShareCodeInChat = showShareCodeInChat;
        result.derpMode = derpMode;
        result.derpMapJson = derpMapJson;
        result.derpMapUrl = derpMapUrl;
        result.regionId = regionId;
        return result;
    }
}
