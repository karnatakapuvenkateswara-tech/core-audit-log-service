# Scenario C — Ambiguous Requirement (Compliance Reporting)

**Given only:** "Regulators need to be able to audit access to client account data."

This is a one-sentence requirement with no endpoint shape, no auth model, and no
definition of "access" or "audit" attached. Rather than guess, the ambiguities were
surfaced explicitly and resolved with the author before writing code.

## Ambiguities identified

1. **Is this a new feature at all, or already covered?** The service already had
   write, query, and export APIs — the "gap" could have been purely upstream
   (guidance on how to log access events) rather than new code.
2. **Who is "regulators," and what should authorize them?** The only identity in
   the system was the `WRITER` role, used for every endpoint so far. Regulators are
   not writers, so reusing that role would blur a boundary the service had
   otherwise kept clean (write vs. read-only).
3. **What does "audit access" mean as a data shape?** Candidates ranged from a raw
   chronological event list to a summarized report with per-actor counts and
   anomaly detection.
4. **How is "access" distinguished from other event types?** `eventType` is
   unconstrained free text — the service has no built-in notion of which values
   mean "read" versus "write."

## Decisions made (via clarifying questions, not assumption)

| Ambiguity | Resolution |
|---|---|
| Scope | Build a **dedicated compliance reporting endpoint** — a new read surface, not just documentation. |
| Authorization | **New `AUDITOR` role**, read-only, separate credentials from `WRITER`. Neither role can do the other's job. |
| Report shape | **Chronological access log** (who, what, when), no aggregation/summary layer. |
| Access filtering | **Caller-filtered, not server-enforced.** No hardcoded allowlist of "access" event types. |

## What was built, at a glance

- A new read-only `AUDITOR` role and a dedicated compliance access-report
  endpoint, filterable by resource and (optionally) event type/time range.
- Archived records are deliberately still included — archival must not make
  evidence of access disappear from a regulator's view.
- Test coverage confirming the two roles stay mutually exclusive and the
  report behaves correctly on both happy and invalid-input paths.

See `README.md` for the exact endpoint/route/config details.

## Explicitly out of scope

- Server-side enforcement of an "access" event vocabulary — the report trusts
  caller-supplied `eventType` values as-is.
- Per-actor summary/aggregation or anomaly detection.
- Regulator-initiated redaction or export (auditors are observe-only).
- Pagination on the report endpoint.
- Provisioning/rotation process for `AUDITOR` credentials in real environments.
