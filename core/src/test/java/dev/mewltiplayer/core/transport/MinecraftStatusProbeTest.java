package dev.mewltiplayer.core.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class MinecraftStatusProbeTest {
    @Test
    void forwardsVanillaStatusAndPlayerCounts() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            CompletableFuture<Void> peer = respondOnce(server);
            MinecraftStatusProbe.Result result = MinecraftStatusProbe.query(
                    new InetSocketAddress("127.0.0.1", server.getLocalPort()));
            assertEquals("Hello Minecraft", result.motd);
            assertEquals("Test 26.3", result.version);
            assertEquals(876, result.protocol);
            assertEquals(2, result.online);
            assertEquals(8, result.max);
            assertTrue(result.latencyMs >= 0);
            peer.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void statusThroughRealTailcat() throws Exception {
        String helper = System.getenv("MEWLTIPLAYER_HELPER");
        Assumptions.assumeTrue(helper != null && !helper.isEmpty(),
                "requires bundled Go helper and live DERP connectivity");
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
             ProcessTailcatBridge host = new ProcessTailcatBridge(Paths.get(helper));
             ProcessTailcatBridge guest = new ProcessTailcatBridge(Paths.get(helper))) {
            CompletableFuture<Void> peer = respondOnce(server);
            String code = host.share(server.getLocalPort()).toCompletableFuture().get(50, TimeUnit.SECONDS);
            ConnectionInfo target = guest.join(code).toCompletableFuture().get(50, TimeUnit.SECONDS);
            MinecraftStatusProbe.Result result = MinecraftStatusProbe.query(target.localEndpoint());
            assertEquals("Hello Minecraft", result.motd);
            assertEquals("Test 26.3", result.version);
            assertEquals(2, result.online);
            assertEquals(8, result.max);
            peer.get(50, TimeUnit.SECONDS);
        }
    }

    private static CompletableFuture<Void> respondOnce(ServerSocket server) {
        return CompletableFuture.runAsync(() -> {
        try (Socket socket = server.accept()) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            int handshakeBytes = readVarInt(input);
            assertTrue(handshakeBytes > 2);
            input.skipBytes(handshakeBytes);
            assertEquals(1, readVarInt(input));
            assertEquals(0, input.readUnsignedByte());
            String json = "{\"version\":{\"name\":\"Test 26.3\",\"protocol\":876}," +
                    "\"players\":{\"max\":8,\"online\":2}," +
                    "\"description\":{\"text\":\"Hello \",\"extra\":[{\"text\":\"Minecraft\"}]}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            DataOutputStream status = new DataOutputStream(body);
            writeVarInt(status, 0);
            writeVarInt(status, bytes.length);
            status.write(bytes);
            byte[] framed = body.toByteArray();
            writeVarInt(output, framed.length);
            output.write(framed);
            output.flush();
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        });
    }

    private static int readVarInt(DataInputStream input) throws Exception {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int v = input.readUnsignedByte();
            result |= (v & 127) << shift;
            if ((v & 128) == 0) return result;
        }
        throw new java.io.IOException("Bad varint");
    }

    private static void writeVarInt(DataOutputStream output, int value) throws Exception {
        while ((value & ~127) != 0) {
            output.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        output.writeByte(value);
    }
}
