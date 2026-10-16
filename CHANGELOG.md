# Changelog

## 0.2.0 — 2026-10-15

This is a draft interoperability release, not a frozen RWP/1 specification.

- New Java sessions emit and require `rwp/1-draft`. The envelope parser also accepts `workers.monocle.dev/v1` for migration tooling; it does not silently downgrade an active session. Existing integrations using the previous default must upgrade both peers or explicitly retain their own legacy adapter.
- Publish the previously local neutral-version and implementation-identity handshake changes, including updated schema, transcripts and Wait example.
- Add a small validated `ImplementationIdentity` helper matching the current binding's software ID/version fields. Identity is self-reported metadata, never authentication or permission.
- Document the current Monocle boundary: Wait and Travel profiles and the optional `dev.monocle.follow.v1` extension are available; highway/stash/kit jobs, reserves, SQLite catalogs, chat and operator controls are not new core RWP actions.
- Clarify TLS deployment, remaining interoperability gaps, and separate library/wire/action versions.
- Add regression checks for session-version isolation, failed-frame sequence handling, identity bounds and UTF-8 frame limits. Package license-bearing Java, source and Javadoc JARs plus a standalone documentation ZIP.

No public marketplace, escrow, chat binding, structured result channel or generic workflow adapter is introduced.

## 0.1.0-SNAPSHOT — initial draft

Initial Java 17 envelope/session helpers, teaching Wait worker, schema and implementation documentation. This was a development snapshot, not a stable protocol release.
