package io.github.exec.rwp;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class EnvelopeSelfTest {
    public static void main(String[] args) throws Exception {
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
        assert Envelope.parse(first.replace(Envelope.VERSION, Envelope.LEGACY_VERSION)).apiVersion().equals(Envelope.LEGACY_VERSION);
        rejects(() -> new WorkerSession(worker).receive(first.replace(Envelope.VERSION, Envelope.LEGACY_VERSION)));
        assert session.receive(first).type().equals("session.accepted");
        rejects(() -> session.receive(first));
        String second = new Envelope(Envelope.VERSION, "message.ack", UUID.randomUUID(), UUID.randomUUID(),
            worker, 1, Instant.now(), new JsonObject()).toJson();
        rejects(() -> session.receive(second.replace(worker.toString(), UUID.randomUUID().toString())));
        rejects(() -> session.receive(second.replace(Envelope.VERSION, Envelope.LEGACY_VERSION)));
        assert session.receive(second).sequence() == 1;
        rejects(() -> Envelope.parse("{invalid"));
        rejects(() -> Envelope.parse(first.replace("\"apiVersion\"", "apiVersion")));
        rejects(() -> Envelope.parse(first + "{}"));
        rejects(() -> Envelope.parse(first.replace(Envelope.VERSION, "workers.monocle.dev/v2")));
        rejects(() -> Envelope.parse(" ".repeat(Envelope.MAX_FRAME_BYTES + 1)));
        UUID report = UUID.randomUUID();
        String retried = new WorkerSession(worker).send("execution.completed", new JsonObject(), report, UUID.randomUUID());
        assert Envelope.parse(retried).messageId().equals(report) && Envelope.parse(retried).sequence() == 0;
        JsonObject oversized = new JsonObject(); oversized.addProperty("text", "🧱".repeat(4_000));
        WorkerSession bounded = new WorkerSession(worker);
        rejects(() -> bounded.send("execution.progress", oversized));
        assert Envelope.parse(bounded.send("worker.observation", new JsonObject())).sequence() == 0 : "Rejected output must not consume a sequence";
        ImplementationIdentity identity = new ImplementationIdentity("org.example.worker", "0.2.0-beta+1");
        JsonObject metadata = identity.toJson(); metadata.addProperty("optional", "ignored");
        assert ImplementationIdentity.parse(metadata).equals(identity);
        rejects(() -> new ImplementationIdentity("org.example bad", "1"));
        rejects(() -> new ImplementationIdentity("a".repeat(129), "1"));
        rejects(() -> new ImplementationIdentity("org.example.worker", ""));
        rejects(() -> new ImplementationIdentity("org.example.worker", "1".repeat(65)));
        rejects(() -> ImplementationIdentity.parse(null));
        rejects(() -> ImplementationIdentity.parse(new JsonObject()));
        metadata.addProperty("version", 2); rejects(() -> ImplementationIdentity.parse(metadata));
        assert new ImplementationIdentity("a".repeat(128), "1".repeat(64)).id().length() == 128;
        int transcripts = 0;
        var blocks = Pattern.compile("```json\\s*([\\s\\S]*?)```").matcher(Files.readString(Path.of("docs/transcripts.md")));
        while (blocks.find()) {
            Envelope frame = Envelope.parse(blocks.group(1));
            assert frame.apiVersion().equals(Envelope.VERSION);
            if (frame.type().equals("session.hello") || frame.type().equals("session.accepted"))
                ImplementationIdentity.parse(frame.payload().getAsJsonObject("implementation"));
            transcripts++;
        }
        assert transcripts >= 15 : "Exercise published full-frame transcripts";
        System.out.println("RWP envelope and session checks passed");
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
