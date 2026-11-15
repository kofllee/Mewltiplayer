package dev.mewltiplayer.core.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InviteCodeTest {
    @Test
    void acceptsCandidateCode() {
        assertTrue(InviteCode.looksLikeTailcatCode("tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC"));
    }

    @Test
    void doesNotTreatRegularAddressesAsInvites() {
        assertFalse(InviteCode.looksLikeTailcatCode("play.example.com:25565"));
        assertFalse(InviteCode.looksLikeTailcatCode("192.168.1.4:25565"));
        assertFalse(InviteCode.looksLikeTailcatCode("tcp"));
        assertFalse(InviteCode.looksLikeTailcatCode(null));
    }

    @Test
    void trimsWhitespace() {
        assertTrue(InviteCode.looksLikeTailcatCode("  tcpGFwWCAQFeXPoeU5SGUoZpWyXgKUByeG3NGd6BaaFMGmhyiucmFrWC  "));
    }
}
