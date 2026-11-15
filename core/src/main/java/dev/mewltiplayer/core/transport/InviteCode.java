package dev.mewltiplayer.core.transport;

import java.util.regex.Pattern;

public final class InviteCode {
    private static final Pattern CANDIDATE = Pattern.compile("tc[A-Za-z0-9_-]{20,8190}");

    private InviteCode() {
    }

    public static boolean looksLikeTailcatCode(String input) {
        return input != null && CANDIDATE.matcher(input.trim()).matches();
    }
}
