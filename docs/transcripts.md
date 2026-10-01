# Wire transcripts for the current test binding

These are **illustrative, complete application frames** for the [current JSON/WebSocket binding](websocket-binding.md), not a promise that every field or ordering is frozen as RWP/1. Each JSON block below contains **one WebSocket text frame**. `W → C` means worker to coordinator and `C → W` means coordinator to worker. The sample IDs and server are fictitious. Authentication occurs in the WebSocket upgrade and is deliberately **not** shown in the frames.

The same worker, job, and execution IDs are reused across the scenarios:

```text
workerId    651be0b7-38fb-4756-a126-a7468d71aefe
jobId       34bc05e5-6973-4629-bd69-644d6fbf3a3b
executionId c9547876-e3d8-4713-97d3-35a751718ccd
commandId   2c553302-6e88-4ce1-af24-40eb6170131f
```

## 1. Hello, observation, and empty reconciliation

The worker connects with its bearer credential, then sends `session.hello` as its **first** frame (worker sequence 0). The coordinator's own sequence independently starts at 0. The worker then reports its scope and asks whether it has any old executions to resume.

**W → C** `session.hello`, sequence 0:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"session.hello","messageId":"00000000-0000-4000-8000-000000000001","correlationId":"10000000-0000-4000-8000-000000000001","sequence":0,"sentAt":"2026-09-30T12:00:00Z","payload":{"workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","supportedVersions":["workers.monocle.dev/v1"],"capabilities":[{"id":"workers.wait.v1"}],"lastHostSequence":0,"lastWorkerSequence":0}}
```

**C → W** `session.accepted`, sequence 0, correlated to hello:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"session.accepted","messageId":"00000000-0000-4000-8000-000000000002","correlationId":"10000000-0000-4000-8000-000000000001","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":0,"sentAt":"2026-09-30T12:00:00Z","payload":{"sessionId":"20000000-0000-4000-8000-000000000001","version":"workers.monocle.dev/v1","maxFrameBytes":16000,"heartbeatSeconds":10,"capabilities":[{"id":"workers.wait.v1"},{"id":"workers.travel.v1"}],"executionEnabled":true,"extensionRouting":true}}
```

**W → C** `worker.observation`, sequence 1. It has no position because Wait does not need one:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"worker.observation","messageId":"00000000-0000-4000-8000-000000000003","correlationId":"10000000-0000-4000-8000-000000000003","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":1,"sentAt":"2026-09-30T12:00:01Z","payload":{"observedAt":"2026-09-30T12:00:01Z","scope":{"server":"play.example.org","dimension":"minecraft:the_nether"}}}
```

**W → C** `state.reconcile`, sequence 2. No checkpoint exists:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"state.reconcile","messageId":"00000000-0000-4000-8000-000000000004","correlationId":"10000000-0000-4000-8000-000000000004","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":2,"sentAt":"2026-09-30T12:00:01Z","payload":{"lastHostSequence":0,"lastWorkerSequence":1,"activeExecutions":[]}}
```

**C → W** `state.reconciled`, sequence 1:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"state.reconciled","messageId":"00000000-0000-4000-8000-000000000005","correlationId":"10000000-0000-4000-8000-000000000004","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":1,"sentAt":"2026-09-30T12:00:01Z","payload":{"decisions":[]}}
```

No job has been authorized by this handshake. On the current Monocle coordinator, an operator must also register/assign the worker to a crew through the operator API before it can receive an execution. That crew step is **not** an RWP wire message.

## 2. A Wait execution succeeds

Continue the same socket above. The operator has submitted a `workers.wait.v1` job targeted at the worker's assigned crew and matching world. The coordinator offers it with host sequence 2. The worker validates and checkpoints it, then accepts and starts. Every durable worker report receives `message.ack` containing the report's `messageId`.

**C → W** `execution.assign`, sequence 2:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.assign","messageId":"00000000-0000-4000-8000-000000000006","correlationId":"10000000-0000-4000-8000-000000000006","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":2,"sentAt":"2026-09-30T12:00:02Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"commandId":"2c553302-6e88-4ce1-af24-40eb6170131f","scope":{"server":"play.example.org","dimension":"minecraft:the_nether"},"action":{"type":"workers.wait.v1","arguments":{"ticks":20}}}}
```

**W → C** `execution.accepted`, sequence 3, echoes the assignment command ID:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.accepted","messageId":"00000000-0000-4000-8000-000000000007","correlationId":"10000000-0000-4000-8000-000000000007","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":3,"sentAt":"2026-09-30T12:00:02Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"commandId":"2c553302-6e88-4ce1-af24-40eb6170131f"}}
```

**C → W** `message.ack`, sequence 3:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"message.ack","messageId":"00000000-0000-4000-8000-000000000008","correlationId":"10000000-0000-4000-8000-000000000007","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":3,"sentAt":"2026-09-30T12:00:02Z","payload":{"messageId":"00000000-0000-4000-8000-000000000007","result":"accepted"}}
```

**W → C** `execution.started`, sequence 4:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.started","messageId":"00000000-0000-4000-8000-000000000009","correlationId":"10000000-0000-4000-8000-000000000009","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":4,"sentAt":"2026-09-30T12:00:02Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1}}
```

**C → W** `message.ack`, sequence 4:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"message.ack","messageId":"00000000-0000-4000-8000-000000000010","correlationId":"10000000-0000-4000-8000-000000000009","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":4,"sentAt":"2026-09-30T12:00:02Z","payload":{"messageId":"00000000-0000-4000-8000-000000000009","result":"accepted"}}
```

**W → C** optional `execution.progress`, sequence 5. It is replaceable telemetry, so no `message.ack` follows:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.progress","messageId":"00000000-0000-4000-8000-000000000011","correlationId":"10000000-0000-4000-8000-000000000011","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":5,"sentAt":"2026-09-30T12:00:03Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"detail":"Waiting · 5 ticks remaining","remainingTicks":5}}
```

**W → C** `execution.completed`, sequence 6:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.completed","messageId":"00000000-0000-4000-8000-000000000012","correlationId":"10000000-0000-4000-8000-000000000012","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":6,"sentAt":"2026-09-30T12:00:04Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1}}
```

**C → W** `message.ack`, sequence 5:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"message.ack","messageId":"00000000-0000-4000-8000-000000000013","correlationId":"10000000-0000-4000-8000-000000000012","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":5,"sentAt":"2026-09-30T12:00:04Z","payload":{"messageId":"00000000-0000-4000-8000-000000000012","result":"accepted"}}
```

The coordinator may now show the job as complete. That means it durably accepted the worker's claim; it does not prove a Minecraft-side effect. A Wait action has no game-world effect to prove.

## 3. Cancellation instead of completion

This is an **alternate branch** from scenario 2 just after the `execution.started` acknowledgement (worker has sent through sequence 4; host has sent through sequence 4). The operator cancels the job. The coordinator's cancellation decision is final before the worker acknowledges it. The worker stops, records cleanup state, and replies. If this were an item-transfer action and cleanup were uncertain, use `"cleanup":"inspection_required"` instead.

**C → W** `execution.cancel`, sequence 5:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.cancel","messageId":"00000000-0000-4000-8000-000000000014","correlationId":"10000000-0000-4000-8000-000000000014","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":5,"sentAt":"2026-09-30T12:00:03Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"commandId":"30000000-0000-4000-8000-000000000001"}}
```

**W → C** `execution.cancelled`, sequence 5:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"execution.cancelled","messageId":"00000000-0000-4000-8000-000000000015","correlationId":"10000000-0000-4000-8000-000000000014","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":5,"sentAt":"2026-09-30T12:00:03Z","payload":{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"cleanup":"acknowledged"}}
```

**C → W** `message.ack`, sequence 6:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"message.ack","messageId":"00000000-0000-4000-8000-000000000016","correlationId":"10000000-0000-4000-8000-000000000014","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":6,"sentAt":"2026-09-30T12:00:03Z","payload":{"messageId":"00000000-0000-4000-8000-000000000015","result":"accepted"}}
```

The worker must not send a new `execution.completed` for this cancelled generation. If a cancellation frame is lost, the coordinator retries the **same cancellation command ID**; the worker should not treat the retry as a new job.

## 4. Reconnect with a saved, still-running Wait

This is another alternate branch after scenario 2's `execution.started` acknowledgement. The worker saved the execution and remaining ticks, then the socket dropped. It creates a **new session**, so both wire sequences restart at 0; the execution ID and generation do **not** reset. The `last*Sequence` values are hints about the old session, not permission to replay it.

**W → C** new `session.hello`, sequence 0:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"session.hello","messageId":"00000000-0000-4000-8000-000000000017","correlationId":"10000000-0000-4000-8000-000000000017","sequence":0,"sentAt":"2026-09-30T12:01:00Z","payload":{"workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","supportedVersions":["workers.monocle.dev/v1"],"capabilities":[{"id":"workers.wait.v1"}],"lastHostSequence":4,"lastWorkerSequence":4}}
```

**C → W** new `session.accepted`, sequence 0:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"session.accepted","messageId":"00000000-0000-4000-8000-000000000018","correlationId":"10000000-0000-4000-8000-000000000017","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":0,"sentAt":"2026-09-30T12:01:00Z","payload":{"sessionId":"20000000-0000-4000-8000-000000000002","version":"workers.monocle.dev/v1","maxFrameBytes":16000,"heartbeatSeconds":10,"capabilities":[{"id":"workers.wait.v1"},{"id":"workers.travel.v1"}],"executionEnabled":true,"extensionRouting":true}}
```

**W → C** fresh `worker.observation`, sequence 1:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"worker.observation","messageId":"00000000-0000-4000-8000-000000000019","correlationId":"10000000-0000-4000-8000-000000000019","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":1,"sentAt":"2026-09-30T12:01:00Z","payload":{"observedAt":"2026-09-30T12:01:00Z","scope":{"server":"play.example.org","dimension":"minecraft:the_nether"}}}
```

**W → C** `state.reconcile`, sequence 2, claims the preserved execution:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"state.reconcile","messageId":"00000000-0000-4000-8000-000000000020","correlationId":"10000000-0000-4000-8000-000000000020","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":2,"sentAt":"2026-09-30T12:01:00Z","payload":{"lastHostSequence":4,"lastWorkerSequence":4,"activeExecutions":[{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1}]}}
```

**C → W** `state.reconciled`, sequence 1, authorizes continuing the same checkpoint:

```json
{"apiVersion":"workers.monocle.dev/v1","type":"state.reconciled","messageId":"00000000-0000-4000-8000-000000000021","correlationId":"10000000-0000-4000-8000-000000000020","workerId":"651be0b7-38fb-4756-a126-a7468d71aefe","sequence":1,"sentAt":"2026-09-30T12:01:00Z","payload":{"decisions":[{"jobId":"34bc05e5-6973-4629-bd69-644d6fbf3a3b","executionId":"c9547876-e3d8-4713-97d3-35a751718ccd","generation":1,"decision":"continue"}]}}
```

The worker now resumes its saved remaining ticks; it does **not** restart the full 20. If the coordinator instead returned `wait`, `cancel`, or `inspect`, the worker would not continue. If the worker had **lost** the Wait checkpoint, the current Monocle coordinator could issue a **new generation** after reconciliation. It must not silently do that for an opaque action with uncertain item/block effects.

## 5. Lost report acknowledgement (delivery, not gameplay retry)

Suppose the coordinator persisted an `execution.completed` report but its `message.ack` was lost. The worker retains the original report with message ID `00000000-0000-4000-8000-000000000012`. On a new session, it sends the **identical type and payload under that ID** with the next new-session sequence. The coordinator may answer `"result":"duplicate"`. It does not run Wait again. A changed payload under that same ID is a conflict and closes the session. Receipt history in the current coordinator is bounded; if it no longer remembers the ID, reconcile rather than assuming the old report can be retried forever.

## What these examples do not establish

- No message here is independent proof of a Minecraft-server action.
- The sample bearer credential, operator API token, and crew assignment are intentionally absent from JSON frames.
- The Java example worker does not implement the reconnect scenario; these frames document the **contract to implement**, not functionality already present in that teaching example.
- HTTP, chat, IRC, and federation bindings are not defined by these WebSocket sequences.
