package dev.mewltiplayer.core.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShareCodeTest {
    @Test
    void acceptsCandidateCode() {
        assertTrue(ShareCode.looksLikeTailcatCode("tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC"));
        assertTrue(ShareCode.looksLikeTailcatCode("tcomFwWCcjS5nKNqAod034nWoJZW0LZqDhhC8U_dKdnDRYQ8uNGFpGQEu"));
    }

    @Test
    void doesNotTreatRegularAddressesAsShareCodes() {
        assertFalse(ShareCode.looksLikeTailcatCode("play.example.com:25565"));
        assertFalse(ShareCode.looksLikeTailcatCode("192.168.1.4:25565"));
        assertFalse(ShareCode.looksLikeTailcatCode("tcp"));
        assertFalse(ShareCode.looksLikeTailcatCode(null));
    }

    @Test
    void forwardedAddressDoesNotTriggerAnotherJoin() {
        String local = ShareCode.localAddress(49152);
        assertEquals("127.0.0.1:49152", local);
        assertFalse(ShareCode.looksLikeTailcatCode(local));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.localAddress(0));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.localAddress(65536));
    }

    @Test
    void trimsWhitespace() {
        assertTrue(ShareCode.looksLikeTailcatCode("  tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC  "));
    }
}
