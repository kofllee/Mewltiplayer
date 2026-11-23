package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MewSettingsTest {
    @Test
    void inactiveDerpFieldsDoNotPreventSaving() throws Exception {
        Path file = Files.createTempDirectory("mew-inactive-settings").resolve("settings.json");
        MewSettings settings = new MewSettings();
        settings.derpMode = MewSettings.AUTO;
        settings.derpMapUrl = "http://example.invalid/not-https";
        settings.derpMapJson = "this is not JSON";
        settings.validate();
        settings.save(file);
        assertEquals(MewSettings.AUTO, MewSettings.load(file).derpMode);
        settings.derpMode = MewSettings.URL;
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.derpMode = MewSettings.MAP;
        assertThrows(IllegalArgumentException.class, settings::validate);
    }

    @Test
    void validCustomDerpMapParsesAndPersists() throws Exception {
        Path file = Files.createTempDirectory("mew-custom-derp").resolve("settings.json");
        MewSettings settings = new MewSettings();
        settings.derpMode = MewSettings.MAP;
        settings.derpMapJson = "{\"Regions\":{\"901\":{\"RegionID\":901,\"RegionCode\":\"test\",\"RegionName\":\"Test Region\",\"Nodes\":[{}]}}}";
        settings.regionId = 901;
        settings.save(file);
        MewSettings loaded = MewSettings.load(file);
        assertEquals(901, loaded.regionId);
        assertEquals("Test Region", DerpRegions.forSettings(loaded).get(0).name);
    }

    @Test
    void loadsDefaultsAndPersistsChoices() throws Exception {
        Path file = Files.createTempDirectory("mew-settings").resolve("settings.json");
        MewSettings defaults = MewSettings.load(file);
        assertTrue(defaults.showShareCodeInChat);
        defaults.showShareCodeInChat = false;
        defaults.regionId = 302;
        defaults.derpMode = MewSettings.URL;
        defaults.shareCodeMode = MewSettings.GLOBAL;
        defaults.derpMapUrl = "https://example.com/map.json";
        defaults.save(file);

        MewSettings restored = MewSettings.load(file);
        assertEquals(302, restored.regionId);
        assertEquals(defaults.derpMapUrl, restored.derpMapUrl);
        assertEquals(MewSettings.GLOBAL, restored.shareCodeMode);
        assertEquals(MewSettings.URL, restored.derpMode);
        assertFalse(restored.showShareCodeInChat);
        assertFalse(restored.copy().showShareCodeInChat);
    }

    @Test
    void migratesLegacyDerpMapUrl() throws Exception {
        Path file = Files.createTempDirectory("mew-legacy-settings").resolve("settings.json");
        Files.write(file, "{\"derpMapUrl\":\"https://example.org/map.json\",\"regionId\":31}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MewSettings settings = MewSettings.load(file);
        assertEquals(MewSettings.URL, settings.derpMode);
        assertEquals(MewSettings.NEW, settings.shareCodeMode);
        assertTrue(settings.showShareCodeInChat);
        assertEquals(31, settings.regionId);
    }

    @Test
    void rejectsUnsafeValues() {
        MewSettings settings = new MewSettings();
        settings.derpMode = MewSettings.URL;
        settings.derpMapUrl = "file:///etc/passwd";
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.derpMapUrl = "http://example.com/derp.json";
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.derpMapUrl = "";
        settings.regionId = -1;
        assertThrows(IllegalArgumentException.class, settings::validate);
    }
}
