package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class MewShareCodesTest {
    @TempDir Path root;

    @Test
    void modeSwitchRetainsKeysAndCodes() throws Exception {
        Path config = root.resolve("config");
        Path world = root.resolve("saves").resolve("test");
        Path worldIdentity = MewShareCodes.identity(MewSettings.WORLD, world, config);
        Path globalIdentity = MewShareCodes.identity(MewSettings.GLOBAL, world, config);
        Files.createDirectories(worldIdentity.getParent());
        Files.createDirectories(config);
        Files.write(worldIdentity, "world key".getBytes(StandardCharsets.UTF_8));
        Files.write(globalIdentity, "global key".getBytes(StandardCharsets.UTF_8));
        MewShareCodes.record(MewSettings.WORLD, world, config, "tcWorld");
        MewShareCodes.record(MewSettings.GLOBAL, world, config, "tcGlobal");
        assertNull(MewShareCodes.identity(MewSettings.NEW, world, config));
        assertEquals("tcWorld", MewShareCodes.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewShareCodes.preview(MewSettings.GLOBAL, world, config));
        MewWorldSettings.setPinned(world, false);
        assertEquals("tcWorld", MewShareCodes.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewShareCodes.preview(MewSettings.GLOBAL, world, config));

        MewShareCodes.reset(MewSettings.WORLD, world, config);
        assertNull(MewShareCodes.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewShareCodes.preview(MewSettings.GLOBAL, world, config));
        MewShareCodes.resetAll(config, root.resolve("saves"));
        assertNull(MewShareCodes.preview(MewSettings.GLOBAL, world, config));
    }

    @Test
    void refusesToDisplayCodeWithoutIdentity() throws Exception {
        Path config = root.resolve("config");
        MewShareCodes.record(MewSettings.GLOBAL, null, config, "tcOrphan");
        assertNull(MewShareCodes.preview(MewSettings.GLOBAL, null, config));
    }

    @Test
    void parsesActualDerpMapRegionEntries() {
        String json = "{\"Regions\":{\"900\":{\"RegionID\":900,\"RegionCode\":\"test\",\"RegionName\":\"Sample\",\"Nodes\":[{\"Name\":\"example\"}]}}}";
        List<DerpRegions.Region> regions = DerpRegions.parse(json);
        assertEquals(900, regions.get(0).id);
        assertEquals("Sample (test, 900)", regions.get(0).label());
        assertThrows(IllegalArgumentException.class, () -> DerpRegions.parse("{\"Regions\":{}}"));
    }
}
