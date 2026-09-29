package io.github.exec.rwp.examples;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.exec.rwp.Envelope;
import io.github.exec.rwp.WorkerSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A teaching example: waits on a timer but never opens Minecraft or claims game-server proof. */
public final class WaitWorker implements WebSocket.Listener {
    private final UUID id = UUID.fromString(required("RWP_WORKER_ID"));
    private final String server = required("RWP_SERVER"), dimension = required("RWP_DIMENSION");
    private final WorkerSession session = new WorkerSession(id);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch done = new CountDownLatch(1);
    private final StringBuilder incoming = new StringBuilder();
    private WebSocket socket;
    private ScheduledFuture<?> pending;
    private String activeExecution = "";

    public static void main(String[] args) throws Exception {
        WaitWorker worker = new WaitWorker();
        URI endpoint = URI.create(required("RWP_URL"));
        if (!"ws".equals(endpoint.getScheme()) && !"wss".equals(endpoint.getScheme()))
            throw new IllegalArgumentException("RWP_URL must use ws:// or wss://");
        HttpClient.newHttpClient().newWebSocketBuilder()
            .header("Authorization", "Bearer " + required("RWP_TOKEN"))
            .buildAsync(endpoint, worker).join();
        worker.done.await();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name);
        return value;
    }

    @Override public void onOpen(WebSocket socket) {
        this.socket = socket;
        JsonObject hello = new JsonObject(); hello.addProperty("workerId", id.toString());
        JsonArray versions = new JsonArray(); versions.add(Envelope.VERSION); hello.add("supportedVersions", versions);
        JsonObject capability = new JsonObject(); capability.addProperty("id", "workers.wait.v1");
        JsonArray capabilities = new JsonArray(); capabilities.add(capability); hello.add("capabilities", capabilities);
        hello.addProperty("lastHostSequence", 0); hello.addProperty("lastWorkerSequence", 0);
        send("session.hello", hello);
        socket.request(1);
    }

    @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
        try {
            incoming.append(text);
            if (incoming.length() > Envelope.MAX_FRAME_BYTES) throw new IllegalArgumentException("Host frame too large");
            if (last) {
                Envelope frame = session.receive(incoming.toString());
                incoming.setLength(0);
                handle(frame);
            }
        } catch (RuntimeException error) {
            System.err.println("RWP message rejected: " + error.getMessage());
            socket.sendClose(1008, "Invalid worker message");
        }
        socket.request(1);
        return null;
    }

    private void handle(Envelope frame) {
        JsonObject payload = frame.payload();
        switch (frame.type()) {
            case "session.accepted" -> {
                observe();
                timer.scheduleAtFixedRate(this::observe, 10, 10, TimeUnit.SECONDS);
                JsonObject reconcile = new JsonObject(); reconcile.addProperty("lastHostSequence", 0);
                reconcile.addProperty("lastWorkerSequence", 1); reconcile.add("activeExecutions", new JsonArray());
                send("state.reconcile", reconcile);
            }
            case "state.reconciled", "message.ack" -> { }
            case "execution.assign" -> assign(payload);
            case "execution.cancel" -> cancel(payload);
            case "protocol.error" -> throw new IllegalArgumentException(payload.get("detail").getAsString());
            default -> throw new IllegalArgumentException("Unsupported host message: " + frame.type());
        }
    }

    private synchronized void assign(JsonObject assignment) {
        String execution = assignment.get("executionId").getAsString();
        if (execution.equals(activeExecution)) return;
        if (!activeExecution.isEmpty()) throw new IllegalStateException("A second assignment requires reconciliation");
        JsonObject action = assignment.getAsJsonObject("action");
        if (!"workers.wait.v1".equals(action.get("type").getAsString())) {
            JsonObject rejected = assignment.deepCopy(); rejected.addProperty("code", "unsupported_action");
            send("execution.rejected", rejected); return;
        }
        long ticks = action.getAsJsonObject("arguments").get("ticks").getAsLong();
        if (ticks < 0 || ticks > 1_728_000) throw new IllegalArgumentException("Invalid Wait duration");
        activeExecution = execution;
        send("execution.accepted", assignment);
        send("execution.started", assignment);
        pending = timer.schedule(() -> {
            synchronized (WaitWorker.this) {
                if (!execution.equals(activeExecution)) return;
                send("execution.completed", assignment); activeExecution = ""; pending = null;
            }
        }, ticks * 50, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancel(JsonObject command) {
        if (pending != null) pending.cancel(false);
        pending = null; activeExecution = "";
        JsonObject report = command.deepCopy(); report.addProperty("cleanup", "acknowledged");
        send("execution.cancelled", report);
    }

    private synchronized void send(String type, JsonObject payload) {
        socket.sendText(session.send(type, payload), true);
    }

    private void observe() {
        JsonObject observation = new JsonObject(); observation.addProperty("observedAt", Instant.now().toString());
        JsonObject scope = new JsonObject(); scope.addProperty("server", server); scope.addProperty("dimension", dimension);
        observation.add("scope", scope); send("worker.observation", observation);
    }

    @Override public java.util.concurrent.CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
        timer.shutdownNow(); done.countDown(); return null;
    }

    @Override public void onError(WebSocket socket, Throwable error) {
        System.err.println("RWP connection failed: " + error.getMessage());
        timer.shutdownNow(); done.countDown();
    }
}
