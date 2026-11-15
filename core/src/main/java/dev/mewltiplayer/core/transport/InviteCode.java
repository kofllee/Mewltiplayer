package dev.mewltiplayer.core.transport;

import java.util.regex.Pattern;

public final class InviteCode {
    // This is only a syntactic recognition heuristic, not a cryptographic validation.
    private static final Pattern CANDIDATE = Pattern.compile("tcp[A-Za-z0-9_-]{13,509}");

    private InviteCode() {
    }

    public static boolean looksLikeTailcatCode(String input) {
        return input != null && CANDIDATE.matcher(input.trim()).matches();
    }
}
