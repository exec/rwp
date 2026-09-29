package io.github.exec.rwp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Common JSON frame for the experimental RWP WebSocket binding. Payload rules remain action-specific. */
public record Envelope(String apiVersion, String type, UUID messageId, UUID correlationId,
                       UUID workerId, long sequence, Instant sentAt, JsonObject payload) {
    public static final String VERSION = "workers.monocle.dev/v1";
    public static final int MAX_FRAME_BYTES = 16_000;

    public Envelope {
        if (!VERSION.equals(apiVersion)) throw new IllegalArgumentException("Unsupported API version");
        if (type == null || !type.matches("[a-z][a-z0-9.-]{0,127}")) throw new IllegalArgumentException("Invalid message type");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(sentAt, "sentAt");
        if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
        payload = Objects.requireNonNull(payload, "payload").deepCopy();
    }

    @Override public JsonObject payload() { return payload.deepCopy(); }

    public String toJson() {
        JsonObject frame = new JsonObject();
        frame.addProperty("apiVersion", apiVersion);
        frame.addProperty("type", type);
        frame.addProperty("messageId", messageId.toString());
        frame.addProperty("correlationId", correlationId.toString());
        if (workerId != null) frame.addProperty("workerId", workerId.toString());
        frame.addProperty("sequence", sequence);
        frame.addProperty("sentAt", sentAt.toString());
        frame.add("payload", payload.deepCopy());
        String json = frame.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) throw new IllegalArgumentException("Frame too large");
        return json;
    }

    public static Envelope parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) throw new IllegalArgumentException("Frame too large");
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON data");
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
            JsonObject frame = parsed.getAsJsonObject();
            JsonElement body = frame.get("payload");
            if (body == null || !body.isJsonObject()) throw new IllegalArgumentException("Expected object payload");
            return new Envelope(string(frame, "apiVersion"), string(frame, "type"),
                uuid(frame, "messageId"), uuid(frame, "correlationId"),
                frame.has("workerId") ? uuid(frame, "workerId") : null,
                number(frame, "sequence"), Instant.parse(string(frame, "sentAt")), body.getAsJsonObject());
        } catch (StackOverflowError nested) {
            throw new IllegalArgumentException("Frame nesting too deep", nested);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed worker frame", malformed);
        } catch (RuntimeException malformed) {
            if (malformed instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("Malformed worker frame", malformed);
        }
    }

    private static String string(JsonObject frame, String key) {
        JsonElement value = frame.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string " + key);
        return value.getAsString();
    }

    private static UUID uuid(JsonObject frame, String key) {
        String value = string(frame, key);
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID " + key);
        return id;
    }

    private static long number(JsonObject frame, String key) {
        JsonElement value = frame.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer " + key);
        long sequence = value.getAsBigDecimal().longValueExact();
        if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
        return sequence;
    }
}
