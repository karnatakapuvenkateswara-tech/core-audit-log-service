# Final Engineering Summary — core-audit-log-service

**As of:** 2026-08-09 · **Author:** K Venkateswara Rao · Built with Claude Code
(see `ai_usage_log.md` for the AI-assistance breakdown and `ATTESTATION.md`)

This document is the top-level engineering summary: plan and rationale,
artifacts delivered, verification performed, risks, trade-offs, assumptions,
and known limitations. It defers details to the more specific docs already in
this repo rather than repeating them — see the map at the bottom.

## 1. Plan and rationale

The assignment was to build a **Core Audit Log Service**: an append-only
event log that downstream systems and regulators can trust — meaning writes
can be verified after the fact as unmodified and unreordered, and access to
sensitive resources can be reported on for compliance purposes.

The design followed from that trust requirement, not from a generic CRUD
template:

- **Append-only by construction, not convention.** `EventRecordRepository`
  extends only `CrudRepository`'s `save`/`findAll`-shaped surface — there is
  no `delete`/`update` method exposed anywhere in the codebase for
  `EventRecord`. An engineer cannot accidentally wire up a mutation path
  because the repository interface doesn't offer one.
- **Tamper-evidence via hash chaining**, not just an immutable table. Every
  record stores a `contentHash` of its own fields and a `previousHash`
  linking to the prior record's `contentHash`, genesis-anchored. This makes
  tampering *detectable* (via `GET /audit/verify`), which is a stronger
  property than tampering merely being *disallowed* by application code —
  it also covers out-of-band modification (e.g. a direct DB edit) that
  bypasses the application entirely. The design was worked out and documented
  independently of Spring/JPA in `audit_prototype.md` (a standalone Python
  reference implementation) before being carried into the real service, so
  the core algorithm could be reasoned about and verified in isolation.
- **Redaction and archival without breaking the chain.** Both are common
  real-world requirements (PII removal, retention limits) that naturally
  conflict with "immutable log" — the design resolves the conflict by never
  removing a record or its hash linkage, only blanking the live `payload`
  (retaining `payloadHash` of the original for verification) and soft-marking
  records `archived` (excluded from live scans, retained in chain order).
- **Two disjoint roles instead of one.** `WRITER` (ingest/query/redact/export/
  verify) and `AUDITOR` (compliance report only) use separate in-memory
  credential pairs with mutually exclusive route authorization. This was a
  deliberate boundary, not an oversight — see below.
- **Ambiguous requirements were resolved by asking, not assuming.** The
  compliance-reporting feature was specified as a single sentence ("Regulators
  need to be able to audit access to client account data"). Rather than
  inferring an endpoint shape, auth model, and definition of "access," the
  ambiguities were enumerated and put to the author as explicit decisions.
  Full process and rationale: `Scenario_C.md`.

## 2. Artifacts delivered

| Artifact | Purpose |
|---|---|
| Spring Boot 3 / Java 21 Maven service (`src/main/java/...`) | The service itself — write, query, redact, export, verify, compliance-report APIs |
| `SecurityConfig` | HTTP Basic auth, `WRITER`/`AUDITOR` roles, route-level authorization |
| `EventHashing`, `AuditVerificationService`, `AuditVerificationController` | Hash-chain computation and `GET /audit/verify` tamper check |
| `EventArchivalService`, `ArchivalProperties` | Retention-age soft-deletion, chain-safe |
| `EventBundle` / `EventBundleManifest`, export endpoint | Signed-manifest export bundles for offline verification |
| `ComplianceController`, `findAccessTrail(...)` | Regulator-facing access report (Scenario C) |
| `dev` / `uat` / `prod` / `test` Spring profiles | Environment-appropriate config, secret handling, and logging verbosity |
| `Dockerfile` | Runtime-only image (build jar first, then image) |
| Test suites (`EventControllerTests`, `AuditVerificationControllerTests`, `ComplianceControllerTests`, `CoreAuditLogServiceApplicationTests`) | Auth-boundary, validation, and round-trip coverage |
| `audit_prototype.md` | Standalone, dependency-free reference implementation of the hash-chain mechanism |
| `Scenario_C.md` | Ambiguity-resolution record for the compliance-reporting requirement |
| `task_decompostion.md` | Feature breakdown by epic, mapped to actual commits/working-tree state |
| `ai_usage_log.md`, `ATTESTATION.md` | AI-assistance disclosure and assignment attestation |
| `README.md` | Full API reference, config reference, run/build/Docker instructions |

## 3. Verification performed (this session)

Before writing this summary, the following were independently checked rather
than taken on faith from the other docs:

- **Git state**: 2 commits on this branch (`33ecee9` skeleton, `486b140`
  write/query API + hash chain). Confirmed the working tree currently has
  modified files (chain/redaction/hashing updates) and untracked new files
  (archival, export, compliance reporting, and all `.md` docs) — matching
  `task_decompostion.md` Epic 9's claim that Epics 4–7 are uncommitted.
- **Build/tests**: `mvnw clean test` initially failed under this machine's
  default `JAVA_HOME` (JDK 17) with `release version 21 not supported`. Re-run
  with `JAVA_HOME` pointed at the installed JDK 21 (bundled with this
  machine's Eclipse install) succeeded: **20/20 tests pass, BUILD SUCCESS**,
  covering `CoreAuditLogServiceApplicationTests` (1), `AuditVerificationControllerTests`
  (6), `ComplianceControllerTests` (5), `EventControllerTests` (8).

This is a real, current result, not a restatement of what the other docs
claim — see §5 for what it implies about the packaged/committed state.

## 4. Risks and trade-offs

- **In-memory, two-account auth model.** `WRITER` and `AUDITOR` are each a
  single hardcoded credential pair per environment (via env vars), not a
  per-user identity system. This is adequate for a service-to-service or
  small-team context but does not give per-actor accountability for *who*
  called the API — only the `actorId` field *inside* an event payload does,
  and that's caller-supplied, not authenticated.
- **Caller-filtered compliance reporting.** The `AUDITOR` report returns
  whatever `eventType` values match the caller's filter — there's no
  server-side notion of "this eventType counts as regulated access." A
  mistagged or omitted `eventType` at write time silently produces an
  incomplete or over-broad regulator report. This was a conscious scope
  decision (`Scenario_C.md`), not an oversight, but it's a real risk if
  downstream teams are inconsistent about event tagging.
  See [10] below for one direction this could be hardened.
- **Unbounded compliance report response.** `GET /api/v1/compliance/access-report`
  returns a plain `List`, no pagination — a resource with a very large access
  history returns everything in one response.
  See [11] below.
- **Pessimistic locking on the hash-chain tail** (`findChainTailForUpdate`)
  serializes all writes through a single lock point. This is the correct
  choice for chain integrity under concurrent writers, but it is a throughput
  ceiling by design — every write is effectively serialized at the DB level.
  No load testing has been done to characterize what write rate this
  supports.
- **Redaction and archival are irreversible.** Once a payload is redacted or
  a record is archived, there's no unredact/unarchive path in the API. This
  matches an append-only log's semantics but means an operator mistake (wrong
  `id` redacted) cannot be undone through the service itself.

## 5. Assumptions

- The two-role, env-var-credential auth model was assumed to be sufficient
  for this assignment's scope; a production deployment fronting real
  regulatory audits would likely need per-user identity (OIDC/SAML) and an
  audit trail of *who queried the audit log*, which doesn't currently exist —
  querying the audit log is itself unaudited.
- `eventType` was assumed to remain free-text/unconstrained, per the Scenario
  C resolution — this was a judgment call made explicitly with the author,
  not a silent default.
- The archival sweep (`audit.archival.after`, default `P90D`) was assumed to
  be a soft-delete/exclude-from-scan operation, not physical deletion —
  consistent with "tamper-evident audit log" semantics, since permanently
  deleting old records would itself be an unrecoverable, unverifiable
  operation on supposedly immutable history.
- PostgreSQL was assumed as the target datastore for all real environments
  (`dev`/`uat`/`prod`); H2 in PostgreSQL-compatibility mode is test-only and
  was assumed adequate for CI/unit-test purposes without needing a real
  Postgres instance in the test pipeline.

## 6. Known limitations (explicitly out of scope)

Carried from `task_decompostion.md` Epic 8 / `Scenario_C.md`, confirmed still
unimplemented as of this session:

1. No server-side enforcement of an "access" event-type vocabulary.
2. No per-actor aggregation/summary or anomaly detection on the compliance
   report — raw chronological trail only.
3. No regulator-initiated redaction or export (`AUDITOR` is read-only by
   design).
4. No pagination on the compliance report endpoint.
5. No `AUDITOR`/`WRITER` credential provisioning or rotation process —
   only the Spring config plumbing to accept credentials from the
   environment.
6. No audit trail of read access to the audit log itself (who called
   `/audit/verify`, the export endpoint, or the compliance report, and when).
7. No load/throughput testing of the pessimistic-lock write path.
8. No CI pipeline configured in this repo — build/test verification in this
   session was manual and local.

## 7. Immediate next steps (not yet done)

- Commit the working-tree changes for Epics 4–7 (redaction, export, archival,
  compliance reporting) — currently uncommitted, per §3.
- Per standing project instruction, push via a feature branch, never directly
  to `develop`.
- Decide commit granularity (one commit per epic vs. a single squashed
  commit) before pushing.

## 8. Document map

| Doc | Contents |
|---|---|
| `README.md` | API reference, config reference, build/run/Docker instructions |
| `Scenario_C.md` | Ambiguity-resolution process for compliance reporting |
| `audit_prototype.md` | Standalone hash-chain reference implementation |
| `task_decompostion.md` | Epic-by-epic feature breakdown and implementation status |
| `ai_usage_log.md` | AI-assistance disclosure |
| `ATTESTATION.md` | Assignment attestation |
| `FINAL_SUMMARY.md` (this file) | Top-level plan, rationale, risks, trade-offs, assumptions, limitations |
