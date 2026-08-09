# AI Usage Log — core-audit-log-service

**Assignment:** Core Audit Log Service
**Author:** K Venkateswara Rao
**Tool used:** Claude Code (Claude, Anthropic)
**Period:** 8/8/2026 – 8/9/2026

This log records where and how AI assistance was used while building this
service, per the assignment attestation requirement.

## Summary

AI assistance was used throughout for implementation, test-writing, and
documentation. Design decisions on ambiguous requirements (see `Scenario_C.md`)
were made by the author via explicit clarifying questions to the assistant,
not by unreviewed AI assumption — the assistant surfaced the ambiguity and
proposed options; the author selected the resolution.

## Areas of use

| Area | How AI was used |
|---|---|
| Project skeleton | Scaffolded Spring Boot 3 (Java 21) Maven layout, profile-based config (`dev`/`uat`/`prod`), Dockerfile, `.gitignore`/`.dockerignore` |
| Event write/query API | Generated `EventRecord`/`EventRequest`/`EventResponse`, `EventController`, `EventService`, `EventRecordRepository`, validation exception handling |
| Hash-chain tamper evidence | Designed and implemented `EventHashing`, `previousHash`/`contentHash` linkage, pessimistic-locked tail read (`findChainTailForUpdate`), `AuditVerificationService`/`Controller`, `ChainViolationType` |
| Standalone prototype | Wrote `audit_prototype.md` — a dependency-free Python reimplementation of the hash-chain mechanism, used to reason about and document the tamper-evidence design independent of the Spring/JPA machinery |
| Redaction | Implemented `POST /api/v1/events/{id}/redact` and `payloadHash` retention so redacting a payload does not break the hash chain |
| Bundle export | Implemented `EventBundle`/`EventBundleManifest` and `GET /api/v1/events/export` |
| Archival | Implemented `ArchivalProperties`/`EventArchivalService`, retention-age soft-delete excluded from live scans but retained in chain linkage |
| Compliance reporting (Scenario C) | Ambiguity in the one-line requirement ("regulators need to audit access to client account data") was surfaced explicitly before coding — see `Scenario_C.md` for the four ambiguities identified and the resolutions chosen by the author. AI then implemented the agreed design: `AUDITOR` role, `ComplianceController`, `findAccessTrail(...)`, and `ComplianceControllerTests` |
| Tests | Generated test suites (`EventControllerTests`, `AuditVerificationControllerTests`, `ComplianceControllerTests`) covering auth boundaries, validation, and round-trip behavior |
| Task decomposition | Generated `task_decompostion.md`, breaking the delivered work into epics grounded in actual commit history and working-tree state |

## What was explicitly NOT delegated to AI assumption

Per `Scenario_C.md`, the following were decided by the author through
clarifying questions rather than left to AI guesswork:

- Whether compliance reporting required a new endpoint vs. reuse of existing APIs
- Whether to introduce a new `AUDITOR` role vs. reuse the existing `WRITER` role
- Whether the report should be a raw chronological log or an aggregated summary
- Whether "access" event filtering should be server-enforced or caller-filtered

## Items deferred (not built, AI or otherwise)

See `task_decompostion.md` Epic 8 for the full list — notably: server-side
enforcement of an access-event vocabulary, per-actor aggregation/anomaly
detection, regulator-initiated redaction/export, pagination on the compliance
report endpoint, and `AUDITOR` credential provisioning/rotation process.

## Review process

All AI-generated code was reviewed and run locally (`mvn clean package` /
test suite) before being considered part of the deliverable. Uncommitted
working-tree changes (redaction, export, archival, compliance reporting) are
tracked in `task_decompostion.md` Epic 9 pending commit.
