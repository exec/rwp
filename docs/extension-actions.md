# Designing a client-specific RWP action

RWP does not need to understand every bot's gameplay algorithm. A coordinator can carry a **versioned, typed extension action** to a worker that explicitly advertises it, while the action owner defines what its arguments mean. This is how a highway job, printer task, stash survey, or guard behavior can be coordinated without making all clients implement the same Minecraft logic.

This is communication interoperability, **not automatic module compatibility**. If clients expose different module names, settings, or job semantics, the coordinator needs a per-client adapter or job template to map the operator's intent into each advertised action. RWP will not perform that translation. If no accurate mapping exists, do not assign the job to that client.

The [core lifecycle](../PROTOCOL.md) still applies: assignment, acceptance/rejection, start, progress, terminal outcome, cancellation, and reconnect reconciliation. An extension changes the **work**, not who has authority to run it.

## Pick an exact name and owner

Use the current binding's `owner.action.vN` shape, such as `dev.example.patrol.v1` or `org.example.highway-repair.v1`. Do not use `workers.*` for a private action: the current Monocle test coordinator reserves that namespace for explicitly implemented portable profiles. A worker advertises the exact ID it supports. `v2` is a different capability, not an implicit upgrade of `v1`.

An action submission contains:

```json
{
  "type": "dev.example.patrol.v1",
  "arguments": {
    "scope": {"server": "play.example.org", "dimension": "minecraft:the_nether"},
    "center": {"x": 0, "y": 116, "z": -5000},
    "radius": 32
  }
}
```

This is **only a shape example**. There is no standardized patrol action or promise that Monocle can execute it. A worker advertising `dev.example.patrol.v1` must define and validate these arguments. The job's own scope must match whatever scope the action requires. The current Monocle coordinator bounds an extension action JSON envelope to 8,192 UTF-8 bytes and routes it only to a connected, reconciled worker advertising the exact type in the correct world and crew.

## Publish an action contract beside your code

Another implementer cannot safely claim compatibility from an ID alone. For each action version, document at least:

| Contract item | What to specify |
| --- | --- |
| Arguments | Required fields, types, ranges, units, defaults, and unknown-field policy. |
| Preconditions | Required world, equipment, inventory, permissions, profile, safe footing, and observed freshness. |
| Start | When `execution.accepted` becomes `execution.started`; which controls the action takes ownership of. |
| Progress | Bounded human-readable detail and any versioned structured fields; how often updates are sent. |
| Success | What the worker actually checked before claiming `execution.completed`. |
| Failure | Stable machine-readable codes and whether the operator can retry safely. |
| Cancellation | How movement/combat/inventory controls stop, who recovers placed containers/drops, and when cleanup needs inspection. |
| Checkpoint | What survives worker restart and which game-side effects may be uncertain. |
| Replay | Which stages can be resumed or re-observed, and which must **never** repeat automatically. |
| Output | Where a result is stored, how large it can be, how it is authenticated, and how retries avoid duplication. |
| Privacy | Which coordinates, chat, inventory, or player data the action emits to the coordinator. |

The most useful contract is one that can answer “what happens if the socket drops **after** an item leaves the player's inventory but **before** the coordinator sees the report?” A generic `execution.completed` is not enough to answer it.

## A conservative execution pattern

1. On `execution.assign`, verify the exact action ID, arguments, world, and local prerequisites. Reject unsupported or unsafe input with `execution.rejected` and a stable `code` before touching the world.
2. Persist the execution ID/generation and a first checkpoint, then send `execution.accepted` and `execution.started` in order.
3. Before non-idempotent stages, persist what you intend to do and what result would make it safe to continue. Observe the server-resolved state afterward.
4. If cancelled, stop issuing new effects immediately, release owned controls, recover what can be recovered, and report `execution.cancelled` with `cleanup: acknowledged` or `inspection_required`.
5. After disconnect, reconcile before resuming. `continue` means the **same checkpoint**, not a new run from the beginning. `inspect` means do not improvise a replay.

A coordinator that only routes opaque extensions cannot judge whether your PvP target was defeated, a block was placed, or a stash was counted correctly. Its UI should say **worker-reported completion** unless a separate verifier has checked the outcome.

## Current limitation: result data

Monocle's present public worker binding supports extension **dispatch and lifecycle**, but not a portable structured result channel. It ignores additional result fields on `execution.completed`; `execution.progress.detail` is short replaceable status text, not an artifact stream. A stash survey that discovers thousands of item counts therefore cannot return them through this binding as a durable structured result today. The owner can use a separately agreed authenticated output channel for an experiment, but should not call that output RWP-interoperable until it has a specified contract. See [open questions](open-questions.md#results-and-artifacts).

## When to propose standardization

Keep an action in your own namespace while its implementation is unique or its success/recovery rules are still changing. Consider a `workers.*` portable profile only when two independent clients need to perform the **same meaning** of work and can agree on arguments, units, completion evidence, cancellation, and crash recovery. It is fine for a mature bot to have many excellent private actions; RWP's value is that other software can discover and supervise them without pretending it knows how to execute them.
