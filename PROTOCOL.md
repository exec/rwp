# RWP/1 draft core

A host authorizes a job; a worker advertises supported actions, receives an execution assignment, reports what it attempted, and reconciles after reconnect. The first binding is JSON over WebSocket. These are protocol semantics, not a requirement to use WebSocket or Monocle.

- A job has a stable UUID. Each worker gets an execution UUID and positive generation. A later generation supersedes earlier reports.
- The host owns assignment, cancellation, and generation. Worker observations describe what the worker saw or did; they are **not** game-server proof.
- `session.hello` negotiates version and capabilities. `worker.observation` reports world scope (and optionally position). `state.reconcile` compares durable execution IDs/generations after reconnect.
- An assignment contains `jobId`, `executionId`, `generation`, `commandId`, world `scope`, and a typed `action`. A worker reports `execution.accepted` or `execution.rejected`, then `execution.started`, then `execution.completed` or `execution.failed`.
- Cancellation is final at the host even before the worker reconnects. `execution.cancelled` acknowledges cleanup separately; a late completion cannot revive cancelled work.
- Each WebSocket direction starts its sequence at zero. A retry uses the same message ID and semantic payload but the next session sequence. A changed payload under an existing message ID is a conflict.
- `workers.wait.v1` and `workers.travel.v1` are the first experimental actions. Travel completion needs a fresh destination observation but still relies on a worker claim. Item drops and other non-idempotent effects must not be blindly replayed after a lost acknowledgement.

The current Monocle binding is loopback-only by default and uses one token per worker, a 16,000-byte inbound frame limit, and explicit reconnect decisions (`continue`, `cancel`, `wait`, or `inspect`). It is a test implementation, not the final RWP/1 authority model. Public job boards, payments, and escrow are out of scope for the core.
