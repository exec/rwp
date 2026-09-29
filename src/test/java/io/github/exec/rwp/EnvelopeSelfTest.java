package io.github.exec.rwp;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.UUID;

public final class EnvelopeSelfTest {
    public static void main(String[] args) {
        UUID worker = UUID.randomUUID();
        WorkerSession session = new WorkerSession(worker);
        JsonObject payload = new JsonObject(); payload.addProperty("note", "hello 🧱");
        Envelope outgoing = Envelope.parse(session.send("worker.observation", payload));
        assert outgoing.sequence() == 0 && outgoing.workerId().equals(worker);
        payload.addProperty("note", "mutated");
        assert outgoing.payload().get("note").getAsString().equals("hello 🧱");
        JsonObject hostPayload = new JsonObject(); hostPayload.addProperty("sessionId", UUID.randomUUID().toString());
        String first = new Envelope(Envelope.VERSION, "session.accepted", UUID.randomUUID(), UUID.randomUUID(),
            worker, 0, Instant.now(), hostPayload).toJson();
        assert session.receive(first).type().equals("session.accepted");
        rejects(() -> session.receive(first));
        String second = new Envelope(Envelope.VERSION, "message.ack", UUID.randomUUID(), UUID.randomUUID(),
            worker, 1, Instant.now(), new JsonObject()).toJson();
        assert session.receive(second).sequence() == 1;
        rejects(() -> Envelope.parse("{invalid"));
        rejects(() -> Envelope.parse(first.replace("\"apiVersion\"", "apiVersion")));
        rejects(() -> Envelope.parse(first + "{}"));
        rejects(() -> Envelope.parse(first.replace(Envelope.VERSION, "workers.monocle.dev/v2")));
        rejects(() -> Envelope.parse(" ".repeat(Envelope.MAX_FRAME_BYTES + 1)));
        UUID report = UUID.randomUUID();
        String retried = new WorkerSession(worker).send("execution.completed", new JsonObject(), report, UUID.randomUUID());
        assert Envelope.parse(retried).messageId().equals(report) && Envelope.parse(retried).sequence() == 0;
        System.out.println("RWP envelope and session checks passed");
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
