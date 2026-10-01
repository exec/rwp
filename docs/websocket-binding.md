# Current JSON/WebSocket test binding

This document describes what an independent worker can exchange with **Monocle's current standalone coordinator**. It is an interoperability target, **not** the final transport-independent RWP/1 standard. The tested version string is `workers.monocle.dev/v1`; changing it requires coordinated changes at both endpoints. The [draft core](../PROTOCOL.md) explains the semantics that should survive a future binding change.

## Connection and trust boundary

The optional endpoint is `ws://127.0.0.1:<interopPort>/v1/interop/workers`. It is disabled by default, separate from Monocle's trusted native worker socket and administrative HTTP API, and binds **only to loopback**. The coordinator config maps a stable worker UUID to a distinct 24–128-character secret. At most 16 such credentials can be configured. During the WebSocket upgrade, send:

```http
Authorization: Bearer <that worker's secret>
```

Do not send a browser `Origin` header. The endpoint is intended for non-browser workers; it rejects an `Origin` during upgrade. Never send the coordinator's admin API token as a worker credential. A second simultaneous session for the same worker UUID is refused. The worker UUID is an account/profile identity in Monocle's current integration; a future neutral identity model is open for discussion.

Do not expose the loopback listener directly to the Internet. If a test must cross machines, put a trusted TLS-terminating proxy/VPN in front of it and use `wss://`, with a deliberate authentication and network policy. This remains an experimental ingress, not a production public bot-hosting service.

## Frame envelope

Each application frame is one **text** WebSocket message containing one JSON object. Binary messages are rejected. The JSON [envelope schema](../schema/worker-envelope.schema.json) and Java [`Envelope`](../src/main/java/io/github/exec/rwp/Envelope.java) cover common syntax; the coordinator also enforces message-specific fields and state.

```json
{
  "apiVersion": "workers.monocle.dev/v1",
  "type": "worker.observation",
  "messageId": "95d1d011-dcbf-4e92-8069-fe4640ed8a53",
  "correlationId": "6c6f1973-30ca-47f6-94c4-49ee37e46b9c",
  "workerId": "651be0b7-38fb-4756-a126-a7468d71aefe",
  "sequence": 1,
  "sentAt": "2026-09-30T12:00:01Z",
  "payload": {"observedAt": "2026-09-30T12:00:01Z", "scope": {"server": "play.example.org", "dimension": "minecraft:the_nether"}}
}
```

| Field | Current binding rule |
| --- | --- |
| `apiVersion` | Exactly `workers.monocle.dev/v1`. |
| `type` | Lowercase dotted message name; only supported types below are accepted. |
| `messageId` | Canonical UUID. Reuse it only for an identical durable report retry. |
| `correlationId` | UUID for tracing a response to a request/report. Do not use it as an authority or deduplication key. |
| `workerId` | Worker UUID on all post-hello inbound frames and all host frames. The hello frame carries the ID in `payload.workerId`; its envelope may omit this field. |
| `sequence` | Nonnegative integer, starting at `0` **separately in each direction for each new socket** and increasing by exactly one per frame. Do not carry it across reconnects. |
| `sentAt` | Parseable ISO-8601 instant. The host still uses receipt time for observation freshness. |
| `payload` | JSON object. Shape depends on `type`. |

The entire UTF-8 frame is limited to **16,000 bytes**. There is no requirement to use this Java library; any implementation that sends and validates the same JSON can participate. The schema deliberately permits additional top-level properties and generic object payloads. A receiver must still validate fields it relies on and reject unsupported state transitions.

## Session opening

The first worker frame must be `session.hello`, sequence `0`, within five seconds of connection. Its payload contains:

```json
{
  "workerId": "651be0b7-38fb-4756-a126-a7468d71aefe",
  "supportedVersions": ["workers.monocle.dev/v1"],
  "capabilities": [{"id": "workers.wait.v1"}],
  "lastHostSequence": 0,
  "lastWorkerSequence": 0
}
```

The worker ID must match the credential. Capability IDs must be unique and match the versioned owner/action shape `owner.action.vN`; advertise only actions actually implemented. The current coordinator accepts up to eight proposed versions and 64 capabilities, but selects only `workers.monocle.dev/v1`. The last-sequence fields are currently checked as nonnegative; **they are not a replay cursor or substitute for `state.reconcile`**.

The host's first frame is `session.accepted`, sequence `0`, with `sessionId`, chosen `version`, `maxFrameBytes`, `heartbeatSeconds`, `capabilities`, `executionEnabled`, and `extensionRouting`. `heartbeatSeconds` describes the transport's connection-loss setting, **not** a required application heartbeat frame. The WebSocket implementation handles ping/pong. The current coordinator advertises Wait and Travel. A worker can advertise an exact namespaced extension if it implements one.

After acceptance, send `worker.observation`, then `state.reconcile`. The host must know a recent world observation before it can assign work. The worker must receive the reconciliation decision before it resumes a saved action. The current host allows at most 64 inbound frames per second per session and closes a connection if its outbound queue reaches 32 frames. Reconnect and reconcile; do not assume a dropped frame was applied.

## Message catalog

These are the currently meaningful message types. Unless noted, each execution payload contains `jobId`, `executionId`, and positive `generation`.

| Type | Direction | Additional payload | Current effect |
| --- | --- | --- | --- |
| `session.hello` | Worker → host | Worker ID, versions, capabilities, last sequence hints. | Begin negotiated session. |
| `session.accepted` | Host → worker | Session ID, version, limits, capabilities. | Connection is authenticated and accepted; no job is authorized yet. |
| `worker.observation` | Worker → host | `observedAt`, `scope`, optional `position` and `name`. | Replaceable world/player snapshot; no durable report receipt. |
| `state.reconcile` | Worker → host | Sequence hints, `activeExecutions`, optional `unresolvedEffects`. | Ask what to do with saved executions. |
| `state.reconciled` | Host → worker | `decisions` with `continue`, `wait`, `cancel`, or `inspect`. | Apply coordinator's current authority to each claim. |
| `execution.assign` | Host → worker | `commandId`, `scope`, `action`. | Offer one typed execution; worker validates before accepting. |
| `execution.accepted` | Worker → host | Assignment `commandId`. | Acknowledge assignment; not yet running. |
| `execution.rejected` | Worker → host | Assignment `commandId`, stable `code`. | Reject unsupported/unsafe assignment. |
| `execution.started` | Worker → host | Execution reference. | Claim action began. |
| `execution.progress` | Worker → host | `detail`, optional action-specific fields such as `remainingTicks`. | Replaceable telemetry, accepted at most once per second; no `message.ack`. |
| `execution.completed` | Worker → host | Execution reference. | Claim terminal success; Travel additionally requires fresh destination observation. |
| `execution.failed` | Worker → host | Stable `code`. | Claim terminal failure. |
| `execution.cancel` | Host → worker | Cancellation `commandId`. | Revoke authority; coordinator cancellation is already final. |
| `execution.cancelled` | Worker → host | `cleanup`: `acknowledged` or `inspection_required`. | Confirm cleanup outcome separately from cancellation decision. |
| `message.ack` | Host → worker | Report `messageId`, `result`: currently `accepted` or `duplicate`. | Confirm durable report receipt only. |
| `protocol.error` | Host → worker | `code`, `detail`. | Reject invalid session/frame; socket then closes. |

The host currently uses `protocol.error` codes `unsupported_version`, `message_id_conflict`, and `invalid_session`. Treat a protocol error as a failed session, not as permission to continue an execution. Malformed credentials are rejected during WebSocket upgrade rather than as a normal application frame.

### Observation and scope

An observation payload has `scope.server` (nonempty multiplayer address) and `scope.dimension` (namespaced dimension ID such as `minecraft:the_nether`). It may include `position: {"x": 0.5, "y": 116, "z": -5000.5}` and a Minecraft `name` matching `[A-Za-z0-9_]{1,16}`. The host uses **receipt time** to judge freshness, not `observedAt` as proof. Current assignment requires a matching scope observation no older than 30 seconds. Travel assignment/start/completion additionally need a position observation no older than five seconds; completion needs a position within the action's radius and matching world. A claimed position is still not independent game-server proof.

### Assignment and reports

`execution.assign.payload` includes the execution reference, a `commandId`, a world `scope`, and `action: {"type": "workers.wait.v1", "arguments": {"ticks": 20}}` or another advertised type. `execution.accepted` and `execution.rejected` echo the assignment command ID. `execution.started` follows acceptance. `execution.completed` follows start. A terminal report is invalid for an unknown/stale generation or after cancellation. Failure/rejection `code` uses lowercase letters, digits, and underscores, beginning with a letter.

The current Monocle coordinator accepts **one unfinished public job per worker**, does not pause or preempt active public actions, and does not run Lua for them. Monocle-native highway/stash work uses a separate private protocol; it does not automatically become an RWP action. This is a binding limitation, not a core RWP requirement.

### Reconciliation

`state.reconcile.payload.activeExecutions` lists the worker's saved `{jobId, executionId, generation}` references (maximum 16 in this binding). `state.reconciled.payload.decisions` returns each reference with `decision`. The current coordinator rejects nonempty `unresolvedEffects` because it has no generic recovery adapter for them. It may safely restart missing Wait/Travel checkpoints under a newer generation, but an extension with possibly executed effects becomes inspection-required. A worker should not resume until it receives a decision.

### Acknowledgement and retry

The host journals each accepted durable execution report and its receipt together. If its `message.ack` is lost, resend the exact same report type and semantic payload with the **same message ID** and the next sequence number. JSON object key order does not affect duplicate detection. The host responds `duplicate` if it still has that receipt; it rejects a changed report under the same ID with `message_id_conflict`. It retains only the latest 128 execution-report receipts per worker; after that window, reconcile instead of assuming an old retry is remembered. Observation and reconciliation are not receipt-cached. Progress is replaceable telemetry and is not acknowledged as a durable report.

The host re-sends an unacknowledged assignment or cancellation after roughly one second under the same command ID. Never interpret its new **frame** ID or session sequence as a new gameplay action.

## Extension routing

For a type such as `dev.example.inspect.v1`, the current host checks the common envelope, world/crew readiness, exact capability advertisement, and an 8,192-byte limit on the action JSON. It does **not** interpret the extension arguments or independently verify its result. The worker must validate its own arguments and define its own checkpoint/cancel rules. A generic extension completion is presented as worker-reported. The current host **does not retain a structured extension result payload**; extra fields on `execution.completed` are ignored. Use an agreed external output channel or wait for an explicit result contract if your action produces data. The reserved `workers.*` namespace cannot be used for arbitrary vendor extensions. See the [extension guide](extension-actions.md).

## Boundaries and examples

The [wire transcripts](transcripts.md) show valid sample frames with fresh session sequences. The [implementation guide](implementation-guide.md) covers persistence and effect safety; the [interop checklist](interoperability.md) covers a real test. The Monocle-specific host implementation currently lives in [`PublicWorkerGateway`](https://github.com/exec/monocle-client/blob/master/host-service/src/main/java/dev/monocle/host/PublicWorkerGateway.java) and its [worker notes](https://github.com/exec/monocle-client/blob/master/docs/workers-api-v1/phase4-worker.md). These are useful evidence of the **current binding**, not the neutral protocol's sole source of authority.
