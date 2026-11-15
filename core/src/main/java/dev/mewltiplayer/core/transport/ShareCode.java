package dev.mewltiplayer.core.transport;

import java.util.regex.Pattern;

public final class ShareCode {
    private static final Pattern CANDIDATE = Pattern.compile("tc[A-Za-z0-9_-]{20,8190}");

    private ShareCode() {
    }

    public static boolean looksLikeTailcatCode(String input) {
        return input != null && CANDIDATE.matcher(input.trim()).matches();
    }

    public static String localAddress(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid local port");
        }
        return "127.0.0.1:" + port;
    }
}
