package dev.mewltiplayer.core.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InviteCodeTest {
    @Test
    void acceptsCandidateCode() {
        assertTrue(InviteCode.looksLikeTailcatCode("tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC"));
        assertTrue(InviteCode.looksLikeTailcatCode("tcomFwWCcjS5nKNqAod034nWoJZW0LZqDhhC8U_dKdnDRYQ8uNGFpGQEu"));
    }

    @Test
    void doesNotTreatRegularAddressesAsInvites() {
        assertFalse(InviteCode.looksLikeTailcatCode("play.example.com:25565"));
        assertFalse(InviteCode.looksLikeTailcatCode("192.168.1.4:25565"));
        assertFalse(InviteCode.looksLikeTailcatCode("tcp"));
        assertFalse(InviteCode.looksLikeTailcatCode(null));
    }

    @Test
    void forwardedAddressDoesNotTriggerAnotherJoin() {
        String local = InviteCode.localAddress(49152);
        assertEquals("127.0.0.1:49152", local);
        assertFalse(InviteCode.looksLikeTailcatCode(local));
        assertThrows(IllegalArgumentException.class, () -> InviteCode.localAddress(0));
        assertThrows(IllegalArgumentException.class, () -> InviteCode.localAddress(65536));
    }

    @Test
    void trimsWhitespace() {
        assertTrue(InviteCode.looksLikeTailcatCode("  tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC  "));
    }
}
