# Implementing RWP in a worker or coordinator

This guide turns the [draft core](../PROTOCOL.md) into an implementation plan without requiring Java, Monocle, or a specific Minecraft version. For exact JSON and network behavior of the **current** test endpoint, see the [WebSocket binding](websocket-binding.md). The core and binding are deliberately separate: another transport may carry the same job/execution semantics with a different frame format.

## The smallest useful implementation

A third-party worker can participate in a meaningful test with one action:

1. Give the worker a stable ID and a private credential for the test coordinator.
2. Connect and advertise one exact capability, ideally `workers.wait.v1` first.
3. Send a fresh observation of its Minecraft server and dimension, then reconcile any saved work.
4. On assignment, validate the scope, capability, and arguments. Accept or reject the exact command.
5. Start the action, report its outcome, and retain durable report IDs until acknowledged.
6. On cancellation or reconnect, stop work until the coordinator's decision is known.

The [Java Wait example](../examples/io/github/exec/rwp/examples/WaitWorker.java) demonstrates the message shape but **omits** durable checkpoints and reconnect. Do not use it as a production recovery implementation. The [interop checklist](interoperability.md) tells you what to test before adding a real game-side action.

## Separate four kinds of state

An implementation becomes fragile when these are merged into one “job status” flag:

| State | Examples | Owner |
| --- | --- | --- |
| Authorization | Job cancelled; execution generation; assigned action. | Coordinator |
| Delivery | Session sequence; pending acknowledgement; message ID. | Both endpoints |
| Worker checkpoint | Remaining Wait ticks; Travel destination; uncertain item drop. | Worker |
| Observation | Current server, dimension, position, visible world/inventory. | Worker reports; game server ultimately decides reality |

An `execution.completed` report is a worker claim about an action. A `message.ack` confirms that the coordinator recorded that claim. Neither implies that the Minecraft server accepted every packet. This distinction is especially important for block placement, item transfer, and combat.

## Identifiers in practice

Suppose one job is assigned to two workers. Both receive the same `jobId` but different `executionId`s. Each execution starts at `generation: 1`. If one worker loses its checkpoint and the coordinator safely reissues a restartable action, the execution ID may remain the same while its generation advances. An old `execution.completed` from generation 1 must not complete generation 2.

An assignment has a `commandId`; an `execution.accepted` includes that same ID. If an assignment is retried, the worker should recognize the command ID and avoid starting a second copy. A cancellation has its own command ID. A worker's `execution.started` report has a `messageId` distinct from the assignment command ID. If the report's acknowledgement is lost, resend the **same report and message ID** with a new session sequence. `correlationId` helps trace related frames but must not be used as an idempotency key.

The current Java `WorkerSession` only generates and checks session sequences. It does not persist a checkpoint, validate a job action, authenticate, reconnect, or deduplicate Minecraft effects.

## A worker's durable record

Before performing real effects, persist a record with at least:

```json
{
  "jobId": "34bc05e5-6973-4629-bd69-644d6fbf3a3b",
  "executionId": "c9547876-e3d8-4713-97d3-35a751718ccd",
  "generation": 1,
  "actionType": "workers.wait.v1",
  "phase": "running",
  "checkpoint": {"remainingTicks": 150},
  "pendingReports": []
}
```

Save state atomically enough for your platform: after a crash it must not claim to have no work when it may have performed a non-idempotent effect. Store each pending durable report's type, semantic payload, and message ID. A saved transport sequence is **not** a substitute for the execution generation; session sequences reset on reconnect. Encrypt or protect files that contain credentials. Never put a worker token into a workflow package or an exported job.

For an action with game-side effects, extend the checkpoint with action-specific stages, observed resource ownership, and unresolved effects. If the answer to “did the server receive this item drop?” is unknown, pause for reconciliation/inspection. Do not replay it just to make the state machine advance.

## Worker event handling

The following is behavioral pseudocode, not a prescribed threading model:

```text
on connection:
    create a new transport session and sequence counters
    authenticate, hello with exact capabilities
    observe current world and position
    reconcile saved active executions
    apply the coordinator's decision before resuming any game-side action

on execution.assign:
    if command/execution/generation already accepted: report the existing state
    else validate world, action ID, arguments, and local safety
    if invalid: send execution.rejected with a stable code
    else: persist the execution and send execution.accepted
    only then begin; send execution.started

on execution.cancel:
    stop issuing new game-side effects immediately
    clean up owned controls and recoverable resources
    persist terminal/inspection state
    send execution.cancelled with cleanup outcome

on disconnection:
    stop accepting new authority from the old session
    retain checkpoint and unacknowledged reports
    reconnect and reconcile before continuing
```

The worker must not treat a socket reconnection as permission to restart its action from step zero. It must also not treat a matching server address string as proof that an action succeeded. Re-observe anything that can change while offline.

## Coordinator event handling

The coordinator should persist the job, selected workers, execution generation, current command, cancellation decision, and durable report receipts. It should choose only workers advertising the exact capability. Before an assignment, check whatever scope/health observations the action requires. Before accepting a report, check the worker identity, execution ID, generation, valid state transition, and whether cancellation has already made it stale.

Cancellation is immediately final at the coordinator. An offline worker may still have cleanup debt. Keep that debt visible until a worker acknowledgement or manual inspection resolves it; do not leave a cancelled job in a “running” state merely because a client is offline. Conversely, do not assume a clean cancellation just because the operator clicked Cancel.

When a worker reconnects, compare its claims with durable coordinator state. Unknown claims should not be adopted as newly authorized work. A missing worker checkpoint may be restartable for Wait or a fresh-position Travel, but an opaque extension with possible item transfers deserves inspection rather than generic replay.

## Experimental action profiles

RWP core does not require any particular Minecraft job type. These profiles are currently implemented on Monocle's test binding and are useful for basic interoperability:

| Capability | Arguments | Intended completion | Recovery constraint |
| --- | --- | --- | --- |
| `workers.wait.v1` | `{"ticks": 20}`; integer 0–1,728,000. | Count that many **active execution** ticks; zero may complete immediately. | Preserve remaining ticks; do not restart the full duration after reconnect. The Java teaching example approximates ticks with wall-clock time and is not a game-tick reference. |
| `workers.travel.v1` | `scope`, numeric `x`, `y`, `z`, optional `radius` (default 2; current binding accepts 0.15–8). | Reach the destination in the specified world on safe footing without mining or placing blocks. | Re-evaluate current position; do not assume old movement packets landed. The current Monocle coordinator requires a fresh destination observation before it records completion. |

The action IDs and envelopes are candidates for portable profiles, not a commitment that all workers will implement them. A worker should advertise only profiles it actually supports. Travel pathfinding, flight, damage avoidance, and anti-cheat behavior are implementation details unless a later profile explicitly specifies them.

### Extensions

A worker can advertise an owner-namespaced action such as `dev.example.stash-scan.v1`. The coordinator can route the bounded `arguments` object to that worker without understanding its internals. The owner must document argument schema, cancellation cleanup, checkpoint/replay behavior, and what “completed” means. Opaque routing does not create cross-client compatibility by itself; two workers advertising the same action ID must intentionally implement the same contract.

Monocle's test coordinator rejects arbitrary extension actions in the reserved `workers.*` namespace and currently limits an extension action envelope to 8,192 UTF-8 bytes. Its own highway/stash implementations are **not** publicly mapped into this binding yet. Do not advertise them as interoperable RWP jobs.

## Failure cases worth implementing before the fun jobs

| Event | Safe result |
| --- | --- |
| Duplicate assignment with the same command ID | No duplicate action; report existing acceptance/state. |
| Same execution ID but newer generation | Stop old authority; reconcile/accept only the new generation. |
| Report acknowledgement lost | Retry identical report/message ID; no repeated game effect. |
| Cancel arrives while action is running | Stop issuing effects, clean up, acknowledge or request inspection. |
| Cancel occurs while worker is offline | On reconnect, coordinator remains cancelled; worker must not resume. |
| Unknown worker checkpoint | Inspect, do not adopt it as authority. |
| Unknown or malformed action | Reject assignment with a stable failure code. |
| World changes during work | Stop scoped effects and send a fresh observation; wait or fail by action policy. |
| Inventory transfer/drop is uncertain after a crash | Inspect or use action-specific server-observation recovery; never blindly repeat. |

The [wire transcripts](transcripts.md) show concrete frame sequences for success, cancellation, and reconnect. The [interoperability checklist](interoperability.md) turns these into a first test plan.
