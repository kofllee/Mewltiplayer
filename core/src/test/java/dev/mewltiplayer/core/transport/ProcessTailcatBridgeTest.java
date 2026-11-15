package dev.mewltiplayer.core.transport;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class ProcessTailcatBridgeTest {
    @Test
    void forwardsTcpThroughTwoHelpers() throws Exception {
        String helper = System.getenv("MEWLTIPLAYER_HELPER");
        Assumptions.assumeTrue(helper != null && !helper.isEmpty(), "requires a compiled helper and live DERP connectivity");

        ExecutorService echoThread = Executors.newSingleThreadExecutor();
        try (ServerSocket echo = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
             ProcessTailcatBridge host = new ProcessTailcatBridge(Paths.get(helper));
             ProcessTailcatBridge guest = new ProcessTailcatBridge(Paths.get(helper))) {

            echoThread.submit(() -> {
                try (Socket connection = echo.accept()) {
                    byte[] buffer = new byte[128];
                    int read = connection.getInputStream().read(buffer);
                    if (read > 0) {
                        connection.getOutputStream().write(buffer, 0, read);
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            String shareCode = host.share(echo.getLocalPort()).toCompletableFuture().get(50, TimeUnit.SECONDS);
            assertTrue(shareCode.startsWith("tc"));
            assertTrue(host.status().toCompletableFuture().get(10, TimeUnit.SECONDS).isSharing());

            ConnectionInfo info = guest.join(shareCode).toCompletableFuture().get(50, TimeUnit.SECONDS);
            assertEquals(ConnectionMode.CONNECTED, info.mode());
            assertTrue(guest.status().toCompletableFuture().get(10, TimeUnit.SECONDS).isJoining());

            try (Socket connection = new Socket()) {
                connection.connect(info.localEndpoint(), 10_000);
                connection.setSoTimeout(20_000);
                byte[] sent = "mewltiplayer".getBytes(StandardCharsets.UTF_8);
                connection.getOutputStream().write(sent);
                byte[] received = new byte[sent.length];
                int length = 0;
                while (length < received.length) {
                    int read = connection.getInputStream().read(received, length, received.length - length);
                    assertTrue(read > 0, "connection closed before returning the bytes");
                    length += read;
                }
                assertArrayEquals(sent, received);
            }

            NetworkStatus network = guest.probe().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(network.mode() == ConnectionMode.DIRECT || network.mode() == ConnectionMode.RELAY
                    || network.mode() == ConnectionMode.CONNECTED);
            assertTrue(network.latencyMs() >= 0);

            guest.stopJoin().toCompletableFuture().get(10, TimeUnit.SECONDS);
            host.stopShare().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertFalse(guest.status().toCompletableFuture().get(10, TimeUnit.SECONDS).isJoining());
            assertFalse(host.status().toCompletableFuture().get(10, TimeUnit.SECONDS).isSharing());
        } finally {
            echoThread.shutdownNow();
        }
    }
}
