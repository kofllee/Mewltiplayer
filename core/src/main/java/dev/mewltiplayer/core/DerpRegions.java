package dev.mewltiplayer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Region metadata from the exact DERP map used by Tailcat, never from a hardcoded list. */
public final class DerpRegions {
    public static final class Region {
        public final int id;
        public final String code;
        public final String name;

        public Region(int id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public String label() {
            String readable = name.isEmpty() ? code : name;
            return readable + " (" + code + ", " + id + ")";
        }
    }

    private DerpRegions() {}

    public static List<Region> parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject regions = root.getAsJsonObject("Regions");
            if (regions == null || regions.size() == 0) throw new IllegalArgumentException("DERP map has no regions");
            List<Region> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : regions.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                int id = value.get("RegionID").getAsInt();
                if (id < 1 || id > 65535 || !entry.getKey().equals(Integer.toString(id))) {
                    throw new IllegalArgumentException("Invalid DERP region ID");
                }
                String code = value.has("RegionCode") ? value.get("RegionCode").getAsString() : "";
                String name = value.has("RegionName") ? value.get("RegionName").getAsString() : "";
                if (!value.has("Nodes") || !value.get("Nodes").isJsonArray()
                        || value.getAsJsonArray("Nodes").size() == 0) {
                    throw new IllegalArgumentException("DERP region has no servers: " + id);
                }
                result.add(new Region(id, code, name));
            }
            result.sort(Comparator.comparingInt(r -> r.id));
            return result;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid DERP map JSON", error);
        }
    }

    public static List<Region> load(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("DERP map URL must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("DERP map HTTP " + connection.getResponseCode());
            }
            try (InputStream stream = connection.getInputStream()) {
                byte[] bytes = new byte[262145];
                int used = 0;
                int n;
                while (used < bytes.length && (n = stream.read(bytes, used, bytes.length - used)) != -1) used += n;
                if (used == bytes.length) throw new IOException("DERP map exceeds 256 KiB");
                return parse(new String(bytes, 0, used, StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    public static List<Region> forSettings(MewSettings settings) throws IOException {
        return MewSettings.MAP.equals(settings.derpMode)
                ? parse(settings.derpMapJson)
                : load(MewSettings.URL.equals(settings.derpMode) ? settings.derpMapUrl : MewSettings.DEFAULT_MAP);
    }
}
