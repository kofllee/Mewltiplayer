package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class MewInvitationsTest {
    @TempDir Path root;

    @Test
    void modeSwitchRetainsKeysAndCodes() throws Exception {
        Path config = root.resolve("config");
        Path world = root.resolve("saves").resolve("test");
        Path worldIdentity = MewInvitations.identity(MewSettings.WORLD, world, config);
        Path globalIdentity = MewInvitations.identity(MewSettings.GLOBAL, world, config);
        Files.createDirectories(worldIdentity.getParent());
        Files.createDirectories(config);
        Files.write(worldIdentity, "world key".getBytes(StandardCharsets.UTF_8));
        Files.write(globalIdentity, "global key".getBytes(StandardCharsets.UTF_8));
        MewInvitations.record(MewSettings.WORLD, world, config, "tcWorld");
        MewInvitations.record(MewSettings.GLOBAL, world, config, "tcGlobal");
        assertNull(MewInvitations.identity(MewSettings.NEW, world, config));
        assertEquals("tcWorld", MewInvitations.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewInvitations.preview(MewSettings.GLOBAL, world, config));
        MewWorldSettings.setPinned(world, false);
        assertEquals("tcWorld", MewInvitations.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewInvitations.preview(MewSettings.GLOBAL, world, config));

        MewInvitations.reset(MewSettings.WORLD, world, config);
        assertNull(MewInvitations.preview(MewSettings.WORLD, world, config));
        assertEquals("tcGlobal", MewInvitations.preview(MewSettings.GLOBAL, world, config));
        MewInvitations.resetAll(config, root.resolve("saves"));
        assertNull(MewInvitations.preview(MewSettings.GLOBAL, world, config));
    }

    @Test
    void refusesToDisplayCodeWithoutIdentity() throws Exception {
        Path config = root.resolve("config");
        MewInvitations.record(MewSettings.GLOBAL, null, config, "tcOrphan");
        assertNull(MewInvitations.preview(MewSettings.GLOBAL, null, config));
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
