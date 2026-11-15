package dev.mewltiplayer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    public int regionId;
    public String derpMapUrl = "";

    public static MewSettings load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new MewSettings();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MewSettings settings = JSON.fromJson(reader, MewSettings.class);
            if (settings == null) {
                throw new IOException("Empty settings file");
            }
            settings.validate();
            return settings;
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

    public void validate() {
        if (regionId < 0 || regionId > 65535) {
            throw new IllegalArgumentException("Invalid DERP region");
        }
        if (derpMapUrl == null) {
            derpMapUrl = "";
        }
        if (derpMapUrl.length() > 1024) {
            throw new IllegalArgumentException("DERP map URL is too long");
        }
        if (!derpMapUrl.isEmpty()) {
            URI uri = URI.create(derpMapUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Custom DERP map requires an HTTPS URL");
            }
        }
    }

    public MewSettings copy() {
        MewSettings value = new MewSettings();
        value.regionId = regionId;
        value.derpMapUrl = derpMapUrl;
        return value;
    }
}
