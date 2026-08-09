# Task Decomposition — core-audit-log-service

Reflects repo state as of 2026-08-09. `[x]` = implemented (committed or present in
working tree), `[ ]` = not started / explicitly out of scope.

## Epic 1: Project Skeleton — `[x]`
Spring Boot 3 (Java 21) Maven scaffold, profile-based config (dev/uat/prod),
env-var-driven secrets, Dockerfile, actuator health/info.
Commit: `33ecee9`

## Epic 2: Event Write & Query API — `[x]`
Append-only write endpoint and filtered/paginated query endpoint, `WRITER`
role and HTTP Basic auth, request validation, test coverage.
Commit: `486b140`

## Epic 3: Tamper-Evidence Hash Chain — `[x]`
Per-record content hash + previous-record linkage, concurrency-safe chain
writes, a verification endpoint that reports the first break found, and a
standalone reference implementation (`audit_prototype.md`) documenting the
mechanism independent of Spring/JPA.
Commit: `486b140` (chain logic) + `audit_prototype.md` untracked

## Epic 4: Redaction — `[x]` *(uncommitted)*
Redact a stored payload without breaking the hash chain — original payload's
hash retained separately for verification.

## Epic 5: Bundle Export — `[x]` *(uncommitted)*
Export a manifest + matching records as a downloadable, independently
verifiable bundle.

## Epic 6: Archival — `[x]` *(uncommitted)*
Retention-age soft-deletion; archived records drop out of live queries but
stay in the chain so verification isn't falsely broken.

## Epic 7: Compliance Reporting — Scenario C — `[x]` *(uncommitted)*
Ambiguous one-line requirement resolved via clarifying questions before
implementation (see `Scenario_C.md`). Delivered: a new read-only `AUDITOR`
role, a dedicated access-report endpoint, and test coverage confirming
`WRITER`/`AUDITOR` stay mutually exclusive.

## Epic 8: Explicitly Out of Scope (deferred, not started) — `[ ]`
- Server-side enforcement of an "access" event vocabulary
- Per-actor summary/aggregation or anomaly detection
- Regulator-initiated redaction or export
- Pagination on the compliance report endpoint
- `AUDITOR` credential provisioning/rotation process

## Epic 9: Repo Hygiene / Pending Actions — `[ ]`
- Commit outstanding working-tree changes (Epics 4–7)
- Decide commit granularity (per-epic vs. squashed)
- Push via feature branch only — never push directly to `develop`
