# First independent interoperability test

This is a practical path for a third-party worker author to test against the **current Monocle standalone coordinator**. It is deliberately narrow: make one Wait job work, then test cancellation and reconnect, then try Travel or a namespaced extension. A passing Wait example proves frame/lifecycle interoperability, **not** safe Minecraft automation.

The [wire transcripts](transcripts.md) show exact frame shapes. The [WebSocket binding](websocket-binding.md) documents authentication, limits, and message fields. The [implementation guide](implementation-guide.md) explains the checkpointing a real bot needs.

## Before connecting

- Use a dedicated worker account/UUID and a disposable local test job. Do not point a first protocol test at a valuable stash, active highway, or production bot.
- Obtain a private token assigned **only** to that worker UUID. It is not a Monocle crew key or admin API token.
- Choose the Minecraft `scope.server` and `scope.dimension` the worker will report. They must match the submitted job exactly.
- Decide which exact capabilities the worker honestly supports. Start with `workers.wait.v1` even if the eventual project is about mining or building.
- Keep credentials out of screenshots, logs, workflow exports, and public issues. The operator API token grants much more authority than a worker token.

## Set up the Monocle test coordinator

The coordinator presently requires Java 25. Build and initialize it using the [Monocle host instructions](https://github.com/exec/monocle-client/blob/master/host-service/README.md). In its private `host-config.json`, add a loopback-only interop port and a per-worker secret:

```json
{
  "interopPort": 6972,
  "interopWorkerTokens": {
    "651be0b7-38fb-4756-a126-a7468d71aefe": "REPLACE_WITH_DISTINCT_RANDOM_SECRET_AT_LEAST_24_CHARACTERS"
  }
}
```

These are **fields to add to the existing generated config**, not a complete replacement file. A real secret should be random and private. Stop the coordinator before editing config, then restart it. The console should print a draft public worker URL ending in `/v1/interop/workers`. This test listener is loopback-only and disabled when `interopPort` is zero; do not port-forward it. Keep the administrative API on its own loopback port (default `6970`).

For the Java teaching worker, set `RWP_URL`, `RWP_TOKEN`, `RWP_WORKER_ID`, `RWP_SERVER`, and `RWP_DIMENSION` as shown in the [README](../README.md), then run `./gradlew runExample`. For another implementation, connect with a WebSocket client that can send an `Authorization: Bearer` upgrade header and follow the [binding](websocket-binding.md). A browser WebSocket is not suitable for this ingress: it cannot set the required header and it supplies an `Origin` that the current listener rejects.

## Admit the worker and submit one job

On the coordinator's **operator** API, retrieve `GET /v1/crews` and choose a crew ID. `GET /v1/workers` should show the connected public worker after hello/observation/reconciliation. Assign the worker using `PUT /v1/crews/{crewId}/workers/{workerId}`. This is a **Monocle coordinator rule**; RWP core has no mandatory crew assignment message.

The operator API requires `Authorization: Bearer <apiToken>`. Mutations require a fresh UUID `Idempotency-Key`; a missing one returns HTTP `428`. The token and key are **not** sent to the worker socket. With a suitable HTTP client, submit the following body to `POST http://127.0.0.1:6970/v1/jobs`:

```json
{
  "id": "34bc05e5-6973-4629-bd69-644d6fbf3a3b",
  "crewId": "REPLACE_WITH_ID_FROM_GET_V1_CREWS",
  "workerIds": ["651be0b7-38fb-4756-a126-a7468d71aefe"],
  "name": "Independent Wait interop",
  "scope": {"server": "play.example.org", "dimension": "minecraft:the_nether"},
  "action": {"type": "workers.wait.v1", "arguments": {"ticks": 20}},
  "priority": 0
}
```

Replace both UUIDs with fresh values for each intended new job and the real worker/crew IDs. The job scope must match the worker's observation. Read `GET /v1/jobs/{jobId}` until the job is complete or inspect its run detail for a rejection. A successful path should contain assignment, acceptance, start, and completion reports, with `message.ack` for each durable worker report. The worker's own logs should never expose its bearer token.

If you use `curl`, keep all API calls on loopback and supply both the admin authorization header and a new idempotency key for **each mutation**, for example:

```sh
curl -H "Authorization: Bearer $RWP_ADMIN_TOKEN" http://127.0.0.1:6970/v1/crews
curl -H "Authorization: Bearer $RWP_ADMIN_TOKEN" http://127.0.0.1:6970/v1/workers
curl -X PUT -H "Authorization: Bearer $RWP_ADMIN_TOKEN" -H "Idempotency-Key: REPLACE_WITH_FRESH_UUID" \
  -H 'Content-Type: application/json' -d '{}' \
  http://127.0.0.1:6970/v1/crews/CREW_UUID/workers/WORKER_UUID
```

Use your own operator UI or a JSON file/body for the job POST; do not paste a real token into an issue or chat transcript. The admin API endpoint is specific to Monocle and is **not** required by RWP.

## Tests to run in order

| Test | Stimulus | Expected safe behavior |
| --- | --- | --- |
| Baseline Wait | Submit `workers.wait.v1` with matching scope. | Worker accepts, starts, completes; coordinator acknowledges reports and shows complete. |
| Unsupported action | Submit an action the worker did not advertise. | Coordinator does not assign it, or worker rejects a wrongly sent assignment; no game effect. |
| Wrong world | Report a different server/dimension before assignment. | No scoped action starts; worker updates observation and waits/rejects appropriately. |
| Cancel while running | Cancel the job from operator API. | Coordinator records cancellation immediately; worker stops and reports cleanup; late success cannot revive it. |
| Socket loss during Wait | Disconnect after `execution.started`, then reconnect with saved remaining time. | New session sequences start at 0; execution ID/generation persist; worker follows `state.reconciled`, not a guessed restart. |
| Lost report acknowledgement | Suppress one `message.ack`, then resend identical report/message ID. | Coordinator returns `duplicate` without re-running an effect. |
| Changed report under reused ID | Reuse a report message ID with different type/payload in an isolated test. | Protocol conflict, not a second valid report. |
| Old generation | Send a late completion for a superseded generation in an isolated test. | Coordinator rejects it; newer execution remains authoritative. |
| Travel, if implemented | Report position, travel within radius on safe footing, observe again, then complete. | No completion without fresh destination observation; no hidden mining/placing. |

Do not run deliberate bad-frame tests against someone else's live coordinator. The Java [`EnvelopeSelfTest`](../src/test/java/io/github/exec/rwp/EnvelopeSelfTest.java) checks only local envelope/session behavior. Monocle also has a [small independent-host probe](https://github.com/exec/monocle-client/blob/master/host-service/src/test/rwp-conformance.mjs) for hello, observation, reconciliation, and sequence rejection; it is not a certification suite or game-backed test.

## Debugging common first-run failures

| Symptom | Likely cause |
| --- | --- |
| WebSocket upgrade rejected | Missing/wrong worker bearer token, wrong endpoint path, browser `Origin`, duplicate active session, or attempting remote plain `ws://`. |
| `session.hello` rejected | Wrong version/UUID, missing capability list, wrong first sequence, or hello deadline exceeded. |
| Connected but no assignment | No crew membership, no reconciliation, stale/mismatched observation, unsupported capability, paused/terminal job, or Travel lacks recent position. |
| Report rejected after assignment | Wrong command ID, execution ID/generation, state transition order, scope changed, or job was cancelled. |
| Job shows “Inspection required” | Missing extension checkpoint or uncertain effects; do not erase the record and retry blindly. |

For reproducible bug reports, provide redacted frames with `type`, IDs, generations, sequences, scope, and coordinator state. Remove tokens, private server/stash coordinates, player messages, and account-identifying data as appropriate. Include what happened **after reconnect**, since that often distinguishes a transport issue from an execution-state issue.

## What we want from a second implementation

Please tell us which of the following is true for your bot:

1. “I can implement these messages as written.” Include the language/runtime and whether it has durable storage.
2. “I can connect, but this particular frame or state transition is underspecified.” Include the smallest counterexample.
3. “My action cannot safely claim completion/cancellation/replay under these rules.” Describe its actual server-observable effect and the inspection/recovery choice you would need.
4. “I need a different transport.” Describe its frame-size, latency, ordering, and authentication constraints without assuming it changes core job authority.

That feedback will shape a stable RWP/1. A protocol used by two independently built workers/coordinators is more valuable than a large catalog of untested action names.
