# Open questions before a stable RWP/1

This repository is an invitation to test a **small common contract** with a second, independently built worker/coordinator. The [core](../PROTOCOL.md) describes candidate semantics; the [WebSocket binding](websocket-binding.md) describes one implementation. This page keeps undecided policy visible rather than smuggling Monocle-specific choices into the protocol.

## Decisions we would like a second implementer to challenge first

| Question | Current experiment | Why it matters |
| --- | --- | --- |
| What is the stable version identifier? | `workers.monocle.dev/v1` is a Monocle binding string. | A neutral protocol should not require another client to claim it is Monocle. Choose a stable RWP identifier only after the first cross-project trial. |
| Is a worker ID the Minecraft account UUID? | Monocle's current test binding uses the profile UUID. | One bot runtime might move accounts or control several; identity, credential scope, and account identity may need separation. |
| How is a Minecraft server identified? | Worker-reported `server` string plus namespaced `dimension`. | DNS aliases, proxies, singleplayer worlds, and server restarts make string equality weaker than world identity. Do not call it cryptographic proof. |
| What is a portable action? | Wait and Travel are experimental; extensions use exact owner-namespaced versions. | Over-standardizing highway/mining algorithms would make capable clients appear incompatible; under-specifying Wait/Travel would make results inconsistent. |
| What does completion prove? | Coordinator records a worker claim; Travel also requires fresh position observation. | A worker may be mistaken or dishonest. Independent server/game evidence is action-specific and not in the core. |
| How does an action return data? | The current public binding records status/detail but ignores extra structured fields on completion. | Scans, surveys, and inventories need a bounded result artifact or separate agreed output channel. |
| Which state must survive a crash? | Execution ID/generation and worker checkpoints; durable reports are receipt-cached. | A worker must not duplicate item drops, placements, or attacks after lost acknowledgements. |
| How are multi-worker jobs represented? | One job can have per-worker executions; Monocle adds crew assignment outside the wire protocol. | Other coordinators may group workers differently. The core should not require a “crew” UI or lane algorithm. |

Concrete counterexamples and proposed JSON are more useful than a vote on an abstract option. The [interoperability checklist](interoperability.md) names the first behaviors to exercise.

## Candidate core versus binding versus extension

| Belongs where? | Examples | Working rule |
| --- | --- | --- |
| Core RWP semantics | Job/execution/generation identity; exact capability advertisement; assignment, reports, cancellation, reconciliation; authority boundaries. | A different transport or client should still recognize the same work state. |
| Transport binding | WebSocket path, bearer header, text-frame size, session sequence, ping/pong, HTTP status or chat framing. | Specify separately for each transport; do not make a chat bot mimic HTTP headers. |
| Portable action profile | Wait tick semantics, Travel radius/arrival/scope requirements. | Version each profile and test it across two implementations before calling it standard. |
| Vendor extension | Highway builder, stash scan, PvP guard, printer, custom workflow, resource transfer. | Owner defines arguments, safety, cancellation, progress, and recovery. Coordinator routes only to exact advertisers. |
| Operator product | Crew screen, WebUI, history retention, job templates, launcher account management. | Useful software, but not a wire obligation for every RWP peer. |
| Later optional ecosystem | Job boards, markets, escrow, reputation, federation. | Do not block basic worker interoperability on these. |

## Wire-level details to settle

### Versioning and unknown fields

The current envelope requires `workers.monocle.dev/v1` and allows extra JSON properties. We need to decide whether a stable binding negotiates a major protocol version, feature set, or both; what an older peer does with unknown message types; and how extension payload schemas are discovered. Until then, implementers should reject unknown **authority-bearing** messages instead of guessing, but tolerate explicitly optional fields they do not need. Do not silently downgrade an action version.

### Identity and authorization

The test endpoint maps one bearer token to one worker UUID; Monocle's coordinator then lets its operator place that worker in a crew. For cross-project use, we need to decide whether worker identity is a stable logical bot identity distinct from a Minecraft account, how credentials rotate, and whether a coordinator can delegate only certain actions. None of these are solved by an advertised capability alone: **can perform** is not the same as **is authorized to perform**.

### World scope and freshness

The current host compares the observed server/dimension to a job and uses host receipt time for freshness (30 seconds generally, five seconds for Travel position). A future scope model may need a server instance ID, a world epoch after restart, or explicit “unknown world” state. An observation must remain an observation, not an unchallengeable source of truth.

### Report receipts versus effect receipts

The current host can say it durably recorded a worker's `execution.completed` report. It cannot say the server accepted a block placement. We should keep these distinct even if an action profile later defines stronger evidence—perhaps a second worker's observation, a world snapshot, or an authoritative server plugin. The generic protocol should not invent a universal “verified” bit without specifying verified **by whom and how**.

### Results and artifacts

Status is not a result format. The current public coordinator ignores additional structured fields on `execution.completed` and only retains a short progress/detail string. For a worker that maps a stash or surveys chunks, we need to decide whether the core should carry bounded result metadata, refer to an immutable external artifact, or leave outputs entirely to the action owner's channel. Any proposal must include size limits, authority, retention, privacy, and retry semantics. Until then, do not claim that a custom extension can return a portable structured inventory report through the current public binding.

### Reconnect and uncertain effects

The current binding keeps only a bounded receipt window and returns `continue`, `wait`, `cancel`, or `inspect` during reconciliation. We need to test whether those four decisions cover an independent bot's crash points. If a bot dropped an item and died before observing pickup, a coordinator should not turn `inspect` into another drop. Any new recovery instruction must explicitly identify which effect can be safely retried.

### Multiple transports

JSON/WebSocket is exercised. A chat or `/msg` binding would have lower throughput, message-size limits, possible reordering, and visibility/privacy concerns. It may need chunking and compact encoding, but that is a **transport project**, not a reason to alter execution generations or cancellation authority. HTTP/IRC/Discord bridges need the same separation. Do not claim chat interoperability until a binding and tests exist.

## A proposed stabilization gate

Before calling this RWP/1 rather than a draft:

1. One independent worker completes Wait against the Monocle coordinator and correctly handles cancellation plus a reconnect.
2. One independent coordinator can accept a worker using the documented lifecycle, or a concrete incompatibility is documented and resolved.
3. At least one real game-backed action demonstrates how scope, completion, and crash recovery are reported without making a false server-proof claim.
4. The stable version identifier, action namespace rule, required fields, unknown-field policy, and error behavior are recorded in normative docs and runnable conformance fixtures.
5. The Java helper library and neutral schema match that frozen contract; Monocle's adapter is updated or explicitly versioned separately.

These are design gates, **not claims that the current repository already passes**. Public markets and escrow can remain later, optional layers with independent trust and dispute policies.
