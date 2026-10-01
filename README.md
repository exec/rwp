<p align="center">
  <img src="assets/rwp-logo.svg" alt="RWP — Redstone Worker Protocol" width="520">
</p>

# Redstone Worker Protocol

RWP is a **draft, implementation-neutral protocol** for coordinating Minecraft workers. A coordinator assigns a typed action to a worker; the worker accepts, reports progress and outcome, and reconciles unfinished work after a disconnect. A worker can be a game client, a bot, or another implementation. A coordinator can be a standalone service or live inside a client. Neither must use Monocle.

The scope is a common **communication contract between independently developed clients/workers and coordinators**—not a universal job engine or a compatibility layer for their modules. RWP lets a coordinator discover exact capabilities and exchange assignments and status, but it does not translate one client's module names, settings, workflows, or algorithms into another's. Coordinators (or their adapters/job templates) must make those mappings for each supported client unless both sides deliberately implement the same versioned action contract. Coordinator-to-coordinator federation is not part of this draft.

This repository is meant to be read and challenged by other implementers. It contains a small Java 17 envelope/session library, a JSON envelope schema, and a runnable example worker. It is **not** a complete bot, a job marketplace, a production authentication service, or a declaration that every Minecraft action is portable.

> **Interoperability status:** The only exercised network binding currently uses JSON text frames over WebSocket with `apiVersion: "workers.monocle.dev/v1"`. That identifier, endpoint, and some payload details are a Monocle **test binding**, not a frozen RWP/1 standard. The Java Wait example has completed a job against Monocle's standalone coordinator. Monocle's own game client implements Wait and Travel on this binding. A live independent game-backed implementation has **not** yet been tested.

## Start here

| If you want to… | Read… |
| --- | --- |
| Understand the roles, IDs, authority, and state transitions | [Protocol core](PROTOCOL.md) |
| Implement a worker or coordinator in any language | [Implementation guide](docs/implementation-guide.md) |
| Speak to the current Monocle test endpoint | [WebSocket binding](docs/websocket-binding.md) |
| See complete JSON exchanges | [Wire transcripts](docs/transcripts.md) |
| Add a client-specific job without pretending it is a universal standard | [Extension action guide](docs/extension-actions.md) |
| Run a first cross-project test and report differences | [Interoperability checklist](docs/interoperability.md) |
| Decide what should become standard versus remain an extension | [Open design questions](docs/open-questions.md) |

The [schema](schema/worker-envelope.schema.json) validates common frame fields. It intentionally does **not** validate every action argument or lifecycle payload. Those contracts are described in the guides and must also be checked by each implementation. Schema-valid does not mean authorized, safe, or completed in Minecraft.

## The idea in one exchange

```text
operator -> coordinator: submit a job for a capable worker
coordinator -> worker:  execution.assign (job ID, execution ID, generation, command ID, scope, action)
worker -> coordinator:  execution.accepted, then execution.started
worker -> coordinator:  execution.progress (optional), then execution.completed or execution.failed
coordinator -> worker:  message.ack for each durable execution report
```

The coordinator owns assignment and cancellation. Worker reports describe the worker's observation; they are **not Minecraft-server proof**. If either side disconnects, it compares the saved execution ID and generation before resuming. A cancelled job cannot be revived by a late report. Work with uncertain non-idempotent effects must be inspected instead of blindly replayed.

Actions are selected by exact, versioned capability ID, for example `workers.wait.v1` or `dev.example.inspect.v1`. Highway building, stash scanning, and other sophisticated work may remain client-specific extensions. RWP can carry and supervise those actions; it does not make unlike implementations interchangeable merely because they both have a “highway builder” module.

## Try the Java example

The library has one runtime dependency, Gson. It targets Java 17 and can be installed locally for experiments:

```sh
./gradlew check
./gradlew publishToMavenLocal
```

The published local coordinate is `io.github.exec:rwp:0.1.0-SNAPSHOT`; it has **not** been released to a package registry. Other languages can implement the JSON contract directly without using this library.

The [Wait worker](examples/io/github/exec/rwp/examples/WaitWorker.java) uses Java's built-in WebSocket client. It never connects to Minecraft; its timer is a teaching example, not a production worker. Configure a dedicated test credential on a compatible coordinator, then run:

```sh
RWP_URL='ws://127.0.0.1:6972/v1/interop/workers' \
RWP_TOKEN='TEST_WORKER_SECRET' RWP_WORKER_ID='TEST_WORKER_UUID' \
RWP_SERVER='play.example.org' RWP_DIMENSION='minecraft:the_nether' \
./gradlew runExample
```

The example does **not** persist checkpoints or reconnect after a lost socket. A production worker must do both, and must not repeat an uncertain Minecraft-side effect just because its completion acknowledgement was lost. Use `wss://` through a trusted TLS endpoint if communication leaves loopback; the current Monocle test listener itself binds only to loopback and must not be exposed directly.

For a first Monocle interop run, configure `interopPort` and one `interopWorkerTokens` entry in its standalone coordinator, assign the connected worker to a crew, and submit a `workers.wait.v1` action. Exact setup, sample API requests, and expected results are in the [interoperability checklist](docs/interoperability.md). A Monocle crew is a property of **that coordinator**, not a required RWP concept.

## Scope and contribution

RWP's near-term scope is capability advertisement, scoped assignments, explicit lifecycle reports, cancellation, and reconnect reconciliation. It does not yet define a public job board, clan identity, cross-coordinator federation, payment, escrow, reputation, chat transport, or a universal gameplay-verification mechanism. Those ideas can be added separately once two independent implementations agree on the core.

If you are building a worker or coordinator, the most valuable feedback is a concrete incompatibility: a message you cannot represent, a state transition you cannot safely implement, or an action whose outcome cannot be verified as currently described. Please include a sample JSON exchange and the recovery behavior you need. The [open questions](docs/open-questions.md) mark areas intentionally left unfrozen.

Licensed under [Apache-2.0](LICENSE). The independent library can be used by GPLv3 clients, including Monocle, without importing Monocle's GPLv3 implementation. See the [Apache Software Foundation's compatibility note](https://www.apache.org/licenses/GPL-compatibility.html).
