# RWP draft core

This document describes **candidate protocol semantics**, independent of HTTP, WebSocket, Minecraft chat, a particular coordinator, or a particular client. The [current WebSocket binding](docs/websocket-binding.md) is more specific and is the only binding exercised so far. Where its behavior is narrower, it is labeled as implementation behavior rather than silently promoted to an RWP rule.

The terms **MUST**, **SHOULD**, and **MAY** below express proposed requirements for implementers of this draft. They are open to revision before a stable RWP/1 release.

## Roles and authority

| Role | Responsibility | Does not imply |
| --- | --- | --- |
| Operator | Authorizes a job and chooses a coordinator/worker through some UI or API. | A particular Monocle screen or API. |
| Coordinator | Selects workers, assigns executions, records reports, cancels authority, and reconciles after reconnect. | Running Minecraft or proving block changes. |
| Worker | Advertises supported actions and performs an accepted assignment in a game client or bot runtime. | Trustworthiness merely because it connected. |
| Minecraft server | Determines actual world state and whether attempted actions succeeded. | RWP connectivity or a coordinator account. |

“Host” and “orchestrator” are reasonable informal synonyms for **coordinator**. The coordinator is not the Minecraft server. A console is only an operator UI for a coordinator. A crew is one possible coordinator grouping; it is not a required wire-level RWP primitive.

The coordinator is authoritative for **which job and execution are authorized**. The worker is authoritative only about its own claimed state and observations. A success report does not prove that the Minecraft server accepted a movement, block placement, inventory transfer, or attack. Applications that need stronger proof must define their own verification policy and state what evidence it uses.

## Scope of interoperability

RWP defines how independently built workers and coordinators **communicate about work**: capabilities, scoped assignments, lifecycle reports, cancellation, and reconciliation. It does **not** provide a shared implementation of jobs or translate client-specific modules, setting names, profiles, workflows, or gameplay strategies. A coordinator or integration adapter must map operator intent to an action and arguments that a particular worker actually advertises and understands. Two clients become interchangeable for an action only when they intentionally implement the **same versioned action contract**; similar module names alone are not enough. Communication between separate coordinators is also outside this draft.

## Identity and scope

These identifiers serve different purposes and MUST NOT be conflated:

| Field | Meaning | Persistence |
| --- | --- | --- |
| `jobId` | Stable operator intent. | Across retries and executions. |
| `executionId` | One worker's attempt to fulfill a job. | Across reconnects of that attempt. |
| `generation` | Positive authority revision for an execution. | Newer generation supersedes older reports. |
| `commandId` | Identity of one assignment or cancellation command. | Reused when retrying the same command. |
| `messageId` | Identity of a wire report/frame for delivery deduplication. | Reused for an identical report retry. |
| `correlationId` | Tracing a related request/response exchange. | Diagnostic, not gameplay authority. |
| `sequence` | Ordering inside the current WebSocket session. | Resets on a new session; not a durable execution revision. |

An assignment also carries a world `scope` (`server`, `dimension`) and a typed `action` (`type`, `arguments`). A worker SHOULD reject or wait on work if its observed game world differs. A coordinator SHOULD require a recent worker observation before assigning scope-sensitive work. The meaning and strength of a server identifier require agreement between implementations; a client-reported address is not cryptographic world identity.

## Capabilities and action ownership

A worker advertises exact, versioned action IDs. A coordinator MUST NOT infer support from a similar name or a lower version. The current test binding uses IDs shaped like `owner.action.vN`, for example `workers.wait.v1` and `dev.example.inspect.v1`. The `workers.*` namespace is reserved for explicitly specified portable action profiles; a client should put its private actions in its own namespace.

The RWP core carries **typed actions**, not a hardcoded list of Minecraft jobs. A coordinator MAY understand an action deeply or MAY route an opaque, bounded extension to a worker advertising that exact ID. In the latter case, the worker owns argument validation and gameplay effects, and the coordinator must not present generic completion as independently verified gameplay. A worker MUST reject an unknown or unsupported action rather than approximate it.

For example, one client might call a module `Highway Builder` and another `Road Worker`, with different width and material settings. RWP does not rename either module or infer equivalent settings. The coordinator can expose one operator-facing “build road” job, but its client-specific adapters must produce the correct advertised action and arguments for each worker—or refuse to assign the job where no compatible mapping exists.

The first experimental portable profiles are [Wait and Travel](docs/implementation-guide.md#experimental-action-profiles). Their details remain open to cross-implementation feedback. Drop Items, Set Profile, highways, stashes, PvP, and workflows are **not** required RWP core actions. Their semantics can be specified by an owner namespace and later standardized only where interoperability justifies it.

## Execution lifecycle

```text
queued --assign--> offered --accepted--> ready --started--> running --completed--> complete
                    \--rejected-------------------------------> failed
                                  ready/running --failed-------> failed
queued/offered/ready/running --cancel--------------------------> cancelled at coordinator
                                             \--cancelled report-> cleanup acknowledged or inspection required
```

The coordinator creates a job and an execution for a selected worker. It sends `execution.assign` with the current generation and a command ID. The worker checks scope, capability, arguments, and local safety before reporting `execution.accepted` or `execution.rejected`. Acceptance is **not** a start. It reports `execution.started` when it begins the action, MAY send replaceable `execution.progress`, and eventually sends `execution.completed` or `execution.failed`. A coordinator MUST reject reports for an unknown execution or stale generation rather than using them to resurrect work.

The coordinator may cancel at any point. Cancellation revokes job authority **at the coordinator immediately**, including when a worker is offline. Delivery and cleanup are separate: the coordinator sends or retries `execution.cancel`, and the worker stops its action and reports `execution.cancelled` when it has handled cleanup. If cleanup is uncertain, report that explicitly; do not claim a clean cancellation. A late `execution.completed` cannot make a cancelled job live again.

Progress is disposable telemetry. Durable execution reports (accept, reject, start, complete, fail, cancelled) need explicit acknowledgement and retry handling. A report's acknowledgement confirms coordinator receipt and state transition, **not** Minecraft-server success.

## Disconnect and reconciliation

Both sides SHOULD persist enough to distinguish an unfinished execution from a new one. On a fresh connection, the worker advertises capabilities, reports its current world/position, and lists its saved active execution IDs and generations. The coordinator compares those claims with its durable records and returns one decision per claim:

| Decision | Worker behavior |
| --- | --- |
| `continue` | Continue the same execution and preserved checkpoint; do not restart from its beginning. |
| `wait` | Retain the checkpoint but do not perform the action until authority/conditions change. |
| `cancel` | Stop and clean up; the coordinator has revoked authority. |
| `inspect` | Do not retry effects automatically. A human or action-specific recovery procedure must resolve uncertainty. |

An unknown execution, a mismatched generation, or uncertain non-idempotent effects SHOULD produce `inspect` rather than a guessed resume. A coordinator MAY issue a new generation for an action known to be safely restartable after a missing checkpoint. That is **action-specific**, not a generic permission to replay arbitrary Minecraft operations.

The current Monocle test coordinator automatically reissues missing **Wait and Travel** checkpoints under a new generation, subject to fresh scope/position checks. It sends opaque extension work to inspection if the worker's checkpoint is missing after it may have started. An independent coordinator can make a different conservative choice while preserving the core rule: do not silently duplicate uncertain effects.

## Idempotency and delivery

Transport delivery, execution reporting, and game effects are three different layers. A network acknowledgement cannot make a dropped item reappear or prove a block was placed. For a lost report acknowledgement, a worker SHOULD retry the **same report type and semantic payload under the same `messageId`**, with a new session-local sequence if necessary. A coordinator SHOULD return an accepted/duplicate receipt for that same report and reject reuse of the ID with changed content. A retry of `execution.assign` or `execution.cancel` SHOULD carry the same `commandId` so the worker does not mistake it for new authority.

The worker MUST keep a checkpoint **before or alongside** a non-idempotent effect if a crash would otherwise make safe replay impossible. If it cannot tell whether an item was dropped, a container was opened, or an attack landed, it must reconcile or request inspection rather than executing the effect again blindly. Observation of present world state can sometimes resolve uncertainty; this belongs to the action adapter, not the envelope library.

## Out of scope

The core does not define public offers/claims, payments, escrow, reputation, cross-coordinator federation, account sharing, Discord/IRC bridges, or transport encryption. It also does not choose a universal clock, movement algorithm, inventory policy, anti-cheat bypass, or gameplay proof. Transport bindings and action profiles may define their own bounded, versioned requirements without making every worker implement them.

See [open questions](docs/open-questions.md) for the decisions a second implementation should help settle.
