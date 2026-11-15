package dev.mewltiplayer.core.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class BundledHelper {
    private BundledHelper() {
    }

    public static synchronized Path extract(Path configDir) throws IOException {
        String platform = platform();
        String fileName = platform.startsWith("windows") ? "mewltiplayer-helper.exe" : "mewltiplayer-helper";
        String resourceDir = "/mewltiplayer/native/" + platform + "/";
        String hash;
        try (InputStream checksum = BundledHelper.class.getResourceAsStream(resourceDir + "sha256.txt")) {
            if (checksum == null) {
                throw new IOException("No Tailcat helper for " + platform);
            }
            hash = new String(readBytes(checksum), StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        }
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid bundled Tailcat checksum");
        }

        Path directory = configDir.resolve("bin").resolve(platform).resolve(hash.substring(0, 16));
        Path executable = directory.resolve(fileName);
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("extract.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
        if (Files.isRegularFile(executable) && hash.equals(sha256(executable))) {
            executable.toFile().setExecutable(true, true);
            return executable;
        }

        Path temporary = Files.createTempFile(directory, "mewltiplayer-", ".tmp");
        try {
            try (InputStream bundled = BundledHelper.class.getResourceAsStream(resourceDir + fileName)) {
                if (bundled == null) {
                    throw new IOException("Bundled Tailcat binary is missing");
                }
                Files.copy(bundled, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!hash.equals(sha256(temporary))) {
                throw new IOException("Bundled Tailcat binary failed SHA-256 verification");
            }
            temporary.toFile().setExecutable(true, true);
            try {
                Files.move(temporary, executable, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING);
            }
            executable.toFile().setExecutable(true, true);
            return executable;
        } finally {
            Files.deleteIfExists(temporary);
        }
        }
    }

    private static String platform() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String system;
        if (os.contains("win")) {
            system = "windows";
        } else if (os.contains("linux")) {
            system = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            system = "darwin";
        } else {
            throw new IOException("Unsupported system for Tailcat: " + os);
        }
        String cpu;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            cpu = "amd64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            cpu = "arm64";
        } else {
            throw new IOException("Unsupported CPU for Tailcat: " + arch);
        }
        return system + "-" + cpu;
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, n);
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) {
                result.append(String.format("%02x", b & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static byte[] readBytes(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) >= 0) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }
}
