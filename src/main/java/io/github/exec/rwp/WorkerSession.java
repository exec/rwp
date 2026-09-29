package io.github.exec.rwp;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Ordered WebSocket-session helper. Create a new instance after each reconnect. */
public final class WorkerSession {
    private final UUID workerId;
    private long nextOutbound, nextInbound;

    public WorkerSession(UUID workerId) { this.workerId = Objects.requireNonNull(workerId); }

    public synchronized String send(String type, JsonObject payload) {
        return send(type, payload, UUID.randomUUID(), UUID.randomUUID());
    }

    /** Supply the original message ID when retrying a report after a lost acknowledgement. */
    public synchronized String send(String type, JsonObject payload, UUID messageId, UUID correlationId) {
        if (nextOutbound == Long.MAX_VALUE) throw new IllegalStateException("Session sequence exhausted");
        String json = new Envelope(Envelope.VERSION, type, messageId, correlationId,
            workerId, nextOutbound, Instant.now(), payload).toJson();
        nextOutbound++;
        return json;
    }

    public synchronized Envelope receive(String json) {
        Envelope frame = Envelope.parse(json);
        if (!workerId.equals(frame.workerId()) || frame.sequence() != nextInbound)
            throw new IllegalArgumentException("Host identity or sequence mismatch");
        if (nextInbound == Long.MAX_VALUE) throw new IllegalStateException("Session sequence exhausted");
        nextInbound++;
        return frame;
    }
}
