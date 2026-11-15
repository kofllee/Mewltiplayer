package dev.mewltiplayer.core.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MinecraftStatusProbe {
    private static final int MAX_PACKET = 1024 * 1024;

    public static final class Result {
        public final String motd;
        public final String version;
        public final int protocol;
        public final int online;
        public final int max;
        public final long latencyMs;
        public final byte[] favicon;

        public Result(String motd, String version, int protocol, int online, int max,
                      long latencyMs, byte[] favicon) {
            this.motd = motd;
            this.version = version;
            this.protocol = protocol;
            this.online = online;
            this.max = max;
            this.latencyMs = latencyMs;
            this.favicon = favicon;
        }
    }

    private MinecraftStatusProbe() {}

    public static Result query(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address, 5000);
            socket.setSoTimeout(12000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream message = new DataOutputStream(body);
            writeVarInt(message, 0); // handshake
            writeVarInt(message, 0); // status requests are independent of gameplay protocol
            writeString(message, "localhost");
            message.writeShort(address.getPort());
            writeVarInt(message, 1); // status
            writePacket(out, body.toByteArray());
            long started = System.nanoTime();
            writePacket(out, new byte[] {0}); // status request
            int size = readVarInt(in);
            if (size < 1 || size > MAX_PACKET) throw new IOException("Invalid Minecraft status length");
            byte[] packet = new byte[size];
            in.readFully(packet);
            DataInputStream response = new DataInputStream(new java.io.ByteArrayInputStream(packet));
            if (readVarInt(response) != 0) throw new IOException("Unexpected Minecraft status packet");
            int jsonLength = readVarInt(response);
            if (jsonLength < 1 || jsonLength > MAX_PACKET) throw new IOException("Invalid Minecraft status JSON");
            byte[] jsonBytes = new byte[jsonLength];
            response.readFully(jsonBytes);
            JsonObject json = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            long latency = Math.max(0, (System.nanoTime() - started) / 1000000L);
            JsonObject version = json.has("version") && json.get("version").isJsonObject()
                    ? json.getAsJsonObject("version") : new JsonObject();
            JsonObject players = json.has("players") && json.get("players").isJsonObject()
                    ? json.getAsJsonObject("players") : new JsonObject();

            byte[] icon = null;
            if (json.has("favicon") && json.get("favicon").isJsonPrimitive()) {
                String value = json.get("favicon").getAsString();
                if (value.startsWith("data:image/png;base64,")) {
                    try {
                        icon = Base64.getDecoder().decode(value.substring(22));
                        if (icon.length > 128 * 1024) icon = null;
                    } catch (IllegalArgumentException ignored) {
                        icon = null;
                    }
                }
            }
            return new Result(description(json.get("description")),
                    text(version, "name", "Unknown"), number(version, "protocol", -1),
                    number(players, "online", 0), number(players, "max", 0), latency, icon);
        } catch (RuntimeException error) {
            throw new IOException("Malformed Minecraft server status", error);
        }
    }

    private static String text(JsonObject o, String key, String fallback) {
        try { return o.has(key) ? o.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int number(JsonObject o, String key, int fallback) {
        try { return o.has(key) ? o.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String description(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            StringBuilder s = new StringBuilder();
            if (o.has("text") && o.get("text").isJsonPrimitive()) s.append(o.get("text").getAsString());
            if (o.has("extra") && o.get("extra").isJsonArray())
                for (JsonElement extra : o.getAsJsonArray("extra")) s.append(description(extra));
            if (s.length() == 0 && o.has("translate")) s.append(o.get("translate").getAsString());
            return s.toString();
        }
        return "";
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writePacket(DataOutputStream out, byte[] bytes) throws IOException {
        writeVarInt(out, bytes.length);
        out.write(bytes);
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            out.writeByte((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int value = in.readUnsignedByte();
            result |= (value & 0x7f) << (7 * i);
            if ((value & 0x80) == 0) return result;
        }
        throw new IOException("Minecraft packet VarInt too long");
    }
}
