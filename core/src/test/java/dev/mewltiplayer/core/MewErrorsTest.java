package dev.mewltiplayer.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MewErrorsTest {
    @Test
    void reportsShortStableCodesWithoutSecrets() {
        assertEquals("Invalid share code (M101)",
                MewErrors.describe(new IllegalStateException("Tailcat helper: invalid_share_code")));
        assertEquals("Could not reach host (M201)",
                MewErrors.describe(new IllegalStateException("Tailcat helper: connection_failed")));
        assertEquals("Connection timed out (M203)",
                MewErrors.describe(new IllegalStateException("Tailcat helper request timed out: join")));
        assertEquals("Connection failed (M299)",
                MewErrors.describe(new IllegalStateException("secret abc123 unexpected")));
        assertEquals("M101", MewErrors.code(new IOException("Tailcat helper: invalid_share_code")));
        assertEquals("M201", MewErrors.code(new IllegalStateException("connection_failed")));
        assertEquals("M202", MewErrors.code(new IOException("Tailcat helper not found")));
        assertEquals("M299", MewErrors.code(new IllegalStateException("secret abc123 unexpected")));
    }
}
