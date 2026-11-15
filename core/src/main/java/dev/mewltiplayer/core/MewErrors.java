package dev.mewltiplayer.core;

import java.util.Locale;

public final class MewErrors {
    private MewErrors() {
    }

    public static String code(Throwable error) {
        String description = describe(error);
        int start = description.lastIndexOf("(M");
        return start < 0 ? "M299" : description.substring(start + 1, description.length() - 1);
    }

    public static String describe(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("invalid_invite")) {
            return "Invalid Tailcat invitation (M101)";
        }
        if (message.contains("connection_failed") || message.contains("probe_failed")) {
            return "Could not reach the host (M201)";
        }
        if (message.contains("not found") || message.contains("helper exited")
                || message.contains("helper failed") || message.contains("no tailcat helper")) {
            return "Tailcat could not start (M202)";
        }
        if (message.contains("timed out") || message.contains("timeout")) {
            return "Connection timed out (M203)";
        }
        if (message.contains("unsupported_protocol_version")) {
            return "Incompatible Tailcat helper (M204)";
        }
        return "Tailcat connection failed (M299)";
    }
}
