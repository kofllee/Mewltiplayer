package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MewSettingsTest {
    @Test
    void loadsDefaultsAndPersistsChoices() throws Exception {
        Path file = Files.createTempDirectory("mew-settings").resolve("settings.json");
        MewSettings defaults = MewSettings.load(file);
        defaults.regionId = 302;
        defaults.derpMode = MewSettings.URL;
        defaults.invitationMode = MewSettings.GLOBAL;
        defaults.derpMapUrl = "https://example.com/map.json";
        defaults.save(file);

        MewSettings restored = MewSettings.load(file);
        assertEquals(302, restored.regionId);
        assertEquals(defaults.derpMapUrl, restored.derpMapUrl);
        assertEquals(MewSettings.GLOBAL, restored.invitationMode);
        assertEquals(MewSettings.URL, restored.derpMode);
    }

    @Test
    void migratesLegacyDerpMapUrl() throws Exception {
        Path file = Files.createTempDirectory("mew-legacy-settings").resolve("settings.json");
        Files.write(file, "{\"derpMapUrl\":\"https://example.org/map.json\",\"regionId\":31}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MewSettings settings = MewSettings.load(file);
        assertEquals(MewSettings.URL, settings.derpMode);
        assertEquals(MewSettings.NEW, settings.invitationMode);
        assertEquals(31, settings.regionId);
    }

    @Test
    void rejectsUnsafeValues() {
        MewSettings settings = new MewSettings();
        settings.derpMapUrl = "file:///etc/passwd";
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.derpMapUrl = "http://example.com/derp.json";
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.derpMapUrl = "";
        settings.regionId = -1;
        assertThrows(IllegalArgumentException.class, settings::validate);
    }
}
