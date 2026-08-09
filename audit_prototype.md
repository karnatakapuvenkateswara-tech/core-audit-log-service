# Audit Log Hash Chain — Standalone Prototype

A minimal, dependency-free demonstration of the tamper-evidence mechanism used in
`core-audit-log-service`: each record stores a hash of its own content plus the hash of
the preceding record (a fixed genesis value for the first). Verifying the chain means
recomputing every hash and checking it matches what's stored — any edit, deletion, or
reordering breaks the chain from that point forward.

This is a single Python file, no external packages, so it can be run and read in
isolation without the Spring Boot/JPA machinery of the real service.

## Run it

```
python audit_chain_prototype.py
```

## The prototype

Save as `audit_chain_prototype.py`:

```python
"""Standalone hash-chain prototype: append-only log with tamper evidence."""

import hashlib
import json
from dataclasses import dataclass, field
from datetime import datetime, timezone

GENESIS_HASH = "0" * 64


def _hash(*parts: str) -> str:
    digest = hashlib.sha256()
    for part in parts:
        digest.update(part.encode("utf-8"))
        digest.update(b"\x00")  # field separator, avoids ambiguous concatenation
    return digest.hexdigest()


@dataclass
class EventRecord:
    event_type: str
    actor_id: str
    payload: str
    timestamp: str
    previous_hash: str
    content_hash: str = field(init=False)

    def __post_init__(self):
        self.content_hash = _hash(
            self.event_type, self.actor_id, self.payload, self.timestamp, self.previous_hash
        )


class AuditLog:
    """In-memory append-only chain. Mirrors EventRecordRepository's tail-lock pattern:
    every append reads the current tail's content_hash before writing the next record."""

    def __init__(self):
        self._records: list[EventRecord] = []

    def append(self, event_type: str, actor_id: str, payload: str) -> EventRecord:
        previous_hash = self._records[-1].content_hash if self._records else GENESIS_HASH
        record = EventRecord(
            event_type=event_type,
            actor_id=actor_id,
            payload=payload,
            timestamp=datetime.now(timezone.utc).isoformat(),
            previous_hash=previous_hash,
        )
        self._records.append(record)
        return record

    def tamper(self, index: int, new_payload: str) -> None:
        """Simulates an out-of-band edit directly on stored data (bypassing append),
        e.g. someone modifying a database row. content_hash is deliberately left as-is,
        which is exactly what makes it detectable."""
        self._records[index].payload = new_payload

    def verify(self) -> dict:
        """Walks the chain in order, recomputing each hash. Reports the first violation
        found, if any, distinguishing two independent failure modes:
          - CONTENT_MISMATCH: stored content_hash doesn't match a recomputation from the
            record's own fields (the record itself was edited).
          - LINK_MISMATCH: stored previous_hash doesn't match the prior record's
            content_hash (the chain was reordered, a record was deleted, or a record was
            spliced in).
        """
        expected_previous = GENESIS_HASH
        for i, record in enumerate(self._records):
            recomputed = _hash(
                record.event_type, record.actor_id, record.payload,
                record.timestamp, record.previous_hash,
            )
            if recomputed != record.content_hash:
                return {
                    "intact": False,
                    "brokenIndex": i,
                    "violation": "CONTENT_MISMATCH",
                    "detail": f"record {i} content_hash does not match its recomputed hash "
                              f"(stored={record.content_hash[:12]}..., "
                              f"recomputed={recomputed[:12]}...)",
                }
            if record.previous_hash != expected_previous:
                return {
                    "intact": False,
                    "brokenIndex": i,
                    "violation": "LINK_MISMATCH",
                    "detail": f"record {i} previous_hash does not match record {i - 1}'s "
                              f"content_hash (chain reordered, spliced, or a record is missing)",
                }
            expected_previous = record.content_hash
        return {"intact": True, "brokenIndex": None, "violation": None, "detail": None}


def _print_verify(label: str, result: dict) -> None:
    print(f"\n{label}")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    log = AuditLog()
    log.append("USER_LOGIN", "user-123", json.dumps({"ip": "10.0.0.1"}))
    log.append("ACCOUNT_VIEWED", "employee-789", json.dumps({"reason": "support ticket"}))
    log.append("ACCOUNT_UPDATED", "employee-789", json.dumps({"field": "address"}))

    print("Chain after 3 appends:")
    for i, r in enumerate(log._records):
        print(f"  [{i}] {r.event_type:16s} content_hash={r.content_hash[:12]}...  "
              f"previous_hash={r.previous_hash[:12]}...")

    _print_verify("Verify (untampered):", log.verify())

    # Directly modify stored content, bypassing append — simulates a tampered row.
    log.tamper(1, json.dumps({"reason": "curiosity"}))

    _print_verify("Verify (after tampering with record 1's payload):", log.verify())
```

## Sample output

This is real output from an actual run (hash values will differ run to run, since
`timestamp` is wall-clock at append time):

```
Chain after 3 appends:
  [0] USER_LOGIN       content_hash=0dbe3eedd967...  previous_hash=000000000000...
  [1] ACCOUNT_VIEWED   content_hash=530ff99ef501...  previous_hash=0dbe3eedd967...
  [2] ACCOUNT_UPDATED  content_hash=afb147263528...  previous_hash=530ff99ef501...

Verify (untampered):
{
  "intact": true,
  "brokenIndex": null,
  "violation": null,
  "detail": null
}

Verify (after tampering with record 1's payload):
{
  "intact": false,
  "brokenIndex": 1,
  "violation": "CONTENT_MISMATCH",
  "detail": "record 1 content_hash does not match its recomputed hash (stored=530ff99ef501..., recomputed=668cb7ed421a...)"
}
```

## How this maps to the real service

| Prototype | `core-audit-log-service` |
|---|---|
| `AuditLog._records` (in-memory list) | `event_records` table, ordered by `receivedAt, id` |
| `EventRecord.content_hash` | `EventRecord.contentHash` (`EventHashing.contentHash(...)`) |
| `EventRecord.previous_hash` | `EventRecord.previousHash` |
| `AuditLog.append()` reading `_records[-1]` | `EventRecordRepository.findChainTailForUpdate()` — pessimistic-locked so concurrent writers can't compute `previousHash` from the same predecessor |
| `AuditLog.verify()` | `AuditVerificationService.verify()`, exposed at `GET /audit/verify` |
| `AuditLog.tamper()` | Not exposed anywhere in the real service — included here only to *demonstrate* detection |
| `CONTENT_MISMATCH` / `LINK_MISMATCH` | `ChainViolationType` enum values returned by `/audit/verify` |

The real service also has to handle two cases this prototype skips: **archived**
records (soft-deleted past a retention age — excluded from the live table scan but
not from `previousHash` linkage, so verification doesn't falsely flag them as breaks)
and **redacted** payloads (sensitive fields blanked out after the fact, with
`payloadHash` — a hash of the *original* payload — stored separately so redaction
doesn't itself break the chain). Both are deliberately left out here to keep the
prototype focused on the core append/verify mechanism.
