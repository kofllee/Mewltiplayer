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
        defaults.derpMapUrl = "https://example.com/map.json";
        defaults.save(file);

        MewSettings restored = MewSettings.load(file);
        assertEquals(302, restored.regionId);
        assertEquals(defaults.derpMapUrl, restored.derpMapUrl);
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
