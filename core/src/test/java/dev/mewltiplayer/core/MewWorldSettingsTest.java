package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MewWorldSettingsTest {
    @TempDir Path world;

    @Test
    void defaultsToTemporaryInvitationAndPersistsPin() throws Exception {
        assertFalse(MewWorldSettings.pinned(world));
        assertFalse(MewWorldSettings.shareViaTailcat(world));
        MewWorldSettings.setPinned(world, true);
        assertTrue(MewWorldSettings.pinned(world));
        MewWorldSettings.setShareViaTailcat(world, true);
        assertTrue(MewWorldSettings.shareViaTailcat(world));
        assertTrue(MewWorldSettings.identity(world).startsWith(world));
        Files.write(MewWorldSettings.identity(world), new byte[] {1, 2, 3});
        MewWorldSettings.setPinned(world, false);
        assertFalse(MewWorldSettings.pinned(world));
        assertTrue(Files.exists(MewWorldSettings.identity(world)), "Mode switches must preserve identities");
        assertTrue(MewWorldSettings.shareViaTailcat(world));
        MewWorldSettings.setShareViaTailcat(world, false);
        assertFalse(MewWorldSettings.shareViaTailcat(world));
    }
}
