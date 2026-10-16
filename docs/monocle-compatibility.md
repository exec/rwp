# Monocle compatibility and migration status

Reviewed against Monocle's local 0.15.4 implementation on 2026-10-15. This is an implementation report, not a requirement for other RWP peers. New Monocle features do not automatically become new RWP messages.

## Three different surfaces

| Surface | What it does | RWP status |
| --- | --- | --- |
| Public worker WebSocket `/v1/interop/workers` | Per-worker authentication, capabilities, assignments, observations, lifecycle receipts, cancellation and reconciliation. | Current `rwp/1-draft` binding. |
| Monocle operator HTTP API and WebUI | Crews, job submission, catalogs, kit delivery, categories, reservations and operator controls. | Monocle-specific product/API, not a required RWP coordinator API. |
| Monocle native worker transport | Highway lanes/resupply, stash scans, kit transfers, chat, settings and richer telemetry. | Private implementation protocol, not interchangeable with the public RWP socket. |

Port 6972 is the usual configured public worker test port; native workers commonly use 6969 and the operator API 6970. These are deployment defaults, not protocol requirements. Native connection success does not demonstrate RWP interoperability.

## Available public actions

| Capability | Worker execution | Coordinator behavior | Limits |
| --- | --- | --- | --- |
| `workers.wait.v1` | Authorized ready execution ticks with durable remaining time. | Built-in validation and lifecycle. | Optional experimental profile; the Java teaching worker approximates ticks with a wall-clock timer. |
| `workers.travel.v1` | Native safe movement toward a fixed destination. | Built-in argument checks and fresh destination observation required for completion. | Position is worker-reported, not independent Minecraft-server proof. |
| `dev.monocle.follow.v1` | Follow a visible player using native walking/adaptive flight. | Opaque exact-capability extension routing. | No automatic TPA, combat, formation or profile installation. |
| Other advertised owner-namespaced extensions | Defined by the implementing worker. | Bounded dispatch and lifecycle supervision. | No arbitrary private use of `workers.*`; no portable structured result channel yet. |

The coordinator advertises its built-in Wait/Travel semantics and `extensionRouting`. Follow need not appear in that built-in list: it is routed only when the selected **worker** advertises the exact extension capability.

### Follow contract summary

`dev.monocle.follow.v1` takes exactly `target`, `radius` and `ticks` in its `arguments`: a canonical lowercase player UUID other than the worker's own, a numeric desired radius of 0.15–8 blocks, and an integer interval of 0–1,728,000 ticks. Zero means until cancelled. The enclosing assignment supplies world scope and execution authority.

```json
{"type":"dev.monocle.follow.v1","arguments":{"target":"00000000-0000-0000-0000-000000000002","radius":2,"ticks":0}}
```

A missing/dead subject produces a waiting state, not travel toward a stale position. Finite intervals include authorized ready ticks spent waiting for the subject. Completion means the assigned interval ended and native controls were released, not that the subject was reached or protected. Reconnect requires reconciliation; uncertain extension checkpoints are not permission to restart. Cancellation acknowledgment waits for the game-thread control-release/landing handoff. Existing native work prevents conflicting takeover.

The authoritative implementation-specific details live beside [Monocle's adapter](https://github.com/exec/monocle-client/blob/master/docs/workers-api-v1/monocle-actions.md). Other clients should advertise this ID only if they deliberately implement the same contract; otherwise publish their own extension.

## Recent stash work is not a core protocol expansion

SQLite catalogs, faster scans, observation batching, hopper/column estimates, deposit verification, kit categories, transfer rotations and advisory reserve floors belong to Monocle's storage/execution and operator surfaces. RWP does not mandate a database, stash schema, module names or inventory algorithm. Monocle currently has no public highway/stash/kit adapter; adding an arbitrary action ID does not create one.

Future adapters must preserve existing inventory receipts, cancellation cleanup, uncertain-effect recovery and resource safety behind an owner-versioned action. The RWP frame cap remains **16,000 UTF-8 bytes**; increases to private message limits do not increase it. Large scan outputs need an explicit bounded result/artifact contract, not megabytes inserted into progress messages.

## Versions and identity

- Java library release: **0.2.0**, separate from the wire identifier **`rwp/1-draft`** and independently versioned action IDs.
- Monocle temporarily accepts `workers.monocle.dev/v1` and replies using that session's selected version. No mid-session switch is supported.
- Java `Envelope.parse` recognizes both identifiers for inspection/migration. `WorkerSession` emits and accepts only the new draft identifier. Users of the old default must upgrade the coordinator or retain their own legacy adapter.
- Software identity is `implementation: {"id":"dev.monocle.client","version":"0.15.4"}`, or the coordinator's analogous `dev.monocle.host` identity. Values are self-reported, not authentication or permission. `ImplementationIdentity` and its [schema](../schema/implementation-identity.schema.json) validate the current bounded shape.

## Deployment and unfinished work

The RWP test listener is optional, disabled by default and loopback-only. Across machines, use a trusted TLS endpoint/proxy and `wss://` with certificate and hostname validation. Do not expose the admin API as worker ingress or send remote plaintext bearer tokens. WebSocket is an HTTP upgrade, not a substitute for TLS. Turnkey remote deployment and simultaneous native/RWP client sessions remain follow-up work.

Automated Monocle checks exercise sockets, persistence, cancellation, reconnect, Wait/Travel, Follow mapping and independent Node mock/probe processes. The separate Java Wait example has previously completed a host job. These do not substitute for a mature independent game-backed client trial; that and independent coordinator review remain stabilization gates. Market, escrow, federation and chat transports remain long-term optional work.
