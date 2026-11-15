package dev.mewltiplayer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Invitation identities are independent: switching modes never destroys another mode's keys. */
public final class MewInvitations {
    private MewInvitations() {}

    public static Path identity(String mode, Path world, Path config) {
        if (MewSettings.GLOBAL.equals(mode)) return config.resolve("global-identity.json");
        if (MewSettings.WORLD.equals(mode) && world != null) return MewWorldSettings.identity(world);
        return null;
    }

    public static Path codeFile(String mode, Path world, Path config) {
        if (MewSettings.GLOBAL.equals(mode)) return config.resolve("global-invite.txt");
        if (MewSettings.WORLD.equals(mode) && world != null) return MewWorldSettings.folder(world).resolve("invite.txt");
        return null;
    }

    public static String preview(String mode, Path world, Path config) {
        Path identity = identity(mode, world, config);
        Path path = codeFile(mode, world, config);
        if (identity == null || path == null || !Files.isRegularFile(identity)) return null;
        try {
            if (!Files.isRegularFile(path)) return null;
            String code = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            return code.startsWith("tc") && code.length() <= 8192 ? code : null;
        } catch (IOException error) {
            return null;
        }
    }

    public static void record(String mode, Path world, Path config, String code) throws IOException {
        Path path = codeFile(mode, world, config);
        if (path == null || identity(mode, world, config) == null || code == null) return;
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".invite-", ".tmp");
        try {
            Files.write(temp, code.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void reset(String mode, Path world, Path config) throws IOException {
        Path identity = identity(mode, world, config);
        if (identity == null) return;
        Files.deleteIfExists(identity);
        Path code = codeFile(mode, world, config);
        if (code != null) Files.deleteIfExists(code);
    }

    public static void resetAll(Path config, Path saves) throws IOException {
        reset(MewSettings.GLOBAL, null, config);
        if (Files.isDirectory(saves)) {
            try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves)) {
                for (Path world : worlds) if (Files.isDirectory(world)) {
                    reset(MewSettings.WORLD, world, config);
                }
            }
        }
    }
}
