# Redstone Worker Protocol (RWP)

RWP is a draft, implementation-neutral way for a host to assign typed work to Minecraft workers and reconcile it after disconnects. This repository contains a small Java 17 library for the current JSON/WebSocket envelope, a runnable Wait-worker example, and a minimal schema. It does **not** contain Monocle or Meteor runtime code.

The current experimental binding uses `workers.monocle.dev/v1` so it can interoperate with Monocle's standalone host. That identifier and the payload contract are **not frozen** as RWP/1; feedback from other client authors is welcome before a stable release. HTTP, WebSocket, Minecraft chat, and other transports can carry the same job/execution semantics, but only the WebSocket binding is exercised here.

## What the Java library does

`Envelope` parses and writes bounded JSON frames, validating the common version, IDs, timestamp, sequence, and payload shape. `WorkerSession` assigns outgoing session sequence numbers and rejects out-of-order host frames. Both are transport-neutral; neither controls Minecraft, authenticates a connection, proves block placement, or runs a job for you. Payload-specific action validation still belongs to the worker/host adapter.

The library has one dependency, Gson, and targets Java 17. Build and run its self-check with `./gradlew check`; `./gradlew publishToMavenLocal` installs `io.github.exec:rwp:0.1.0-SNAPSHOT` for local experiments. No package registry release is published yet.

## Try the example

The [Wait worker](examples/io/github/exec/rwp/examples/WaitWorker.java) uses Java's built-in WebSocket client. Give it a dedicated test credential configured on a compatible host, assign that worker to a crew, then submit a `workers.wait.v1` job from the host operator API or UI:

```sh
RWP_URL='ws://127.0.0.1:6972/v1/interop/workers' \
RWP_TOKEN='TEST_WORKER_SECRET' RWP_WORKER_ID='TEST_WORKER_UUID' \
RWP_SERVER='play.example.org' RWP_DIMENSION='minecraft:the_nether' \
./gradlew runExample
```

Use `wss://` for non-loopback transport. The example never connects to Minecraft; it only waits and reports its own timer. It is a protocol teaching tool, not a production bot. The [envelope schema](schema/worker-envelope.schema.json) describes the shared frame fields; [PROTOCOL.md](PROTOCOL.md) records the minimal lifecycle and safety rules.

The example does not persist checkpoints or reconnect after a lost socket. A production worker must reconcile unfinished executions and retry unacknowledged reports without repeating game-side effects.

Licensed under [Apache-2.0](LICENSE). This independent code can be used by GPLv3 clients, including Monocle, without importing Monocle's GPLv3 implementation. See the [Apache Software Foundation's compatibility note](https://www.apache.org/licenses/GPL-compatibility.html).
