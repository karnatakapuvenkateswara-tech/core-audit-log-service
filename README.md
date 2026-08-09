# core-audit-log-service

Spring Boot 3 (Java 21) Maven service for the Core Audit Log: an append-only,
hash-chained event log with write, query, redaction, export, tamper
verification, and compliance-reporting APIs, backed by `dev` / `uat` / `prod`
configuration profiles and a runtime Docker image.

## Layout

```
src/main/java/com/example/audit/CoreAuditLogServiceApplication.java
src/main/java/com/example/audit/config/SecurityConfig.java             # roles, HTTP Basic, route authorization
src/main/java/com/example/audit/event/EventController.java             # write / query / redact / export
src/main/java/com/example/audit/event/ComplianceController.java        # regulator access report
src/main/java/com/example/audit/event/AuditVerificationController.java # hash-chain verification
src/main/resources/application.properties        # shared defaults, picks active profile
src/main/resources/application-dev.properties
src/main/resources/application-uat.properties
src/main/resources/application-prod.properties
src/test/resources/application-test.properties
Dockerfile                                        # runtime-only image, expects a pre-built jar
```

## Build

```
mvn clean package
```

Produces `target/core-audit-log-service.jar`.

## Run locally

```
# dev is the default profile
java -jar target/core-audit-log-service.jar

# explicit profile
SPRING_PROFILES_ACTIVE=uat java -jar target/core-audit-log-service.jar
```

## Authentication & Roles

All endpoints require HTTP Basic auth except `/actuator/health` and
`/actuator/info`, which are open. There are two in-memory roles, each with
its own credential pair:

| Role | Credentials (env vars) | Can call |
|---|---|---|
| `WRITER` | `WRITE_API_USER` / `WRITE_API_PASS` | `POST /api/v1/events`, `GET /api/v1/events`, `GET /api/v1/events/export`, `POST /api/v1/events/{id}/redact`, `GET /audit/verify` |
| `AUDITOR` | `AUDIT_API_USER` / `AUDIT_API_PASS` | `GET /api/v1/compliance/**` |

`WRITER` and `AUDITOR` are mutually exclusive: `AUDITOR` credentials are
rejected on every `WRITER`-gated route, and `WRITER` credentials are rejected
on the compliance report. There is no role that can call both.

## API Reference

### `POST /api/v1/events` — write an event

Requires `WRITER`. Appends a single event to the log.

Request body — `eventType`, `actorId`, `resourceType`, `resourceId`,
`payload`, `timestamp` are all required:

```json
{
  "eventType": "USER_LOGIN",
  "actorId": "user-123",
  "resourceType": "SESSION",
  "resourceId": "session-456",
  "payload": { "ip": "10.0.0.1" },
  "timestamp": "2026-08-08T12:00:00Z"
}
```

Returns `201 Created` with the stored record, including a generated `id`,
server-side `receivedAt`, and the hash-chain fields (`previousHash`,
`contentHash`, `payloadHash`). There is no update or delete endpoint — the
JPA repository (`EventRecordRepository`) only exposes `save`, so the log is
append-only by construction, not just convention.

```
curl -u audit_writer_dev:audit_writer_dev \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user-123","resourceType":"SESSION","resourceId":"session-456","payload":{"ip":"10.0.0.1"},"timestamp":"2026-08-08T12:00:00Z"}' \
  http://localhost:8080/api/v1/events
```

### `GET /api/v1/events` — query events

Requires `WRITER`. Paginated (`Pageable`, default page size 50), filterable
by any combination of query params (all optional):

| Param | Type | Meaning |
|---|---|---|
| `actorId` | string | exact match |
| `resourceType` | string | exact match |
| `resourceId` | string | exact match |
| `eventType` | string | exact match |
| `from` | instant | lower bound on `timestamp` |
| `to` | instant | upper bound on `timestamp` |
| `archived` | boolean | filter by archival state |

```
curl -u audit_writer_dev:audit_writer_dev \
  "http://localhost:8080/api/v1/events?resourceId=session-456&page=0&size=20"
```

### `POST /api/v1/events/{id}/redact` — redact a payload

Requires `WRITER`. Blanks out the stored payload for the given event `id`
while leaving the record (and the hash chain) intact. The original payload's
hash (`payloadHash`) is retained separately so redaction can be verified
without exposing the original content, and does not itself break the chain.

```
curl -u audit_writer_dev:audit_writer_dev -X POST \
  http://localhost:8080/api/v1/events/{id}/redact
```

### `GET /api/v1/events/export` — export a bundle

Requires `WRITER`. Returns a downloadable `EventBundle` (JSON) — a manifest
(`exportedAt`, `actorId`, `resourceId`, `recordCount`, `bundleHash`) plus the
matching records. `bundleHash` covers the sequence of exported records'
`contentHash` values, so a verifier can confirm the exported file itself
wasn't reordered, truncated, or altered after export — separate from the
per-record chain verification below. `actorId` and `resourceId` are both
optional filters.

```
curl -u audit_writer_dev:audit_writer_dev \
  "http://localhost:8080/api/v1/events/export?resourceId=session-456" \
  -o audit-export.json
```

### `GET /audit/verify` — verify chain integrity

Requires `WRITER`. Walks the full hash chain in order, recomputing each
record's `contentHash` and checking `previousHash` linkage against the prior
record. Reports the first violation found, if any:

| Violation | Meaning |
|---|---|
| `CONTENT_HASH_MISMATCH` | stored `contentHash` doesn't match a recomputation from the record's own fields |
| `PAYLOAD_HASH_MISMATCH` | record isn't marked redacted, but its live payload no longer hashes to the stored `payloadHash` — payload was modified outside the redaction path |
| `PREVIOUS_HASH_MISMATCH` | `previousHash` doesn't match the preceding record's `contentHash` — chain reordered, spliced, or a record is missing |
| `INVALID_GENESIS` | the first record in the chain doesn't point back to the genesis hash |

```
curl -u audit_writer_dev:audit_writer_dev http://localhost:8080/audit/verify
```

See `audit_prototype.md` for a standalone, dependency-free reference
implementation of the hash-chain append/verify mechanism.

### `GET /api/v1/compliance/access-report` — regulator access report

Requires `AUDITOR`. Returns the full chronological event trail for a given
`resourceId`, in a fixed `timestamp ASC, id ASC` order (deterministic,
independent of any caller-supplied sort). `resourceId` is required;
`eventType`, `from`, `to` are optional narrowing filters.

Not filtered by `archived` — an archived record is still evidence an access
happened, so archival does not cause it to silently drop out of a
regulator's report. The endpoint does not enforce any fixed vocabulary of
"access" event types; it returns whatever `eventType` values match the
filters supplied.

```
curl -u audit_reader_dev:audit_reader_dev \
  "http://localhost:8080/api/v1/compliance/access-report?resourceId=session-456"
```

See `Scenario_C.md` for the ambiguity-resolution process behind this
endpoint's design, and its Explicitly Out of Scope section for known gaps
(no server-side access-type vocabulary, no aggregation, no pagination).

### `GET /actuator/health`, `GET /actuator/info`

Open, no auth required. `prod` never shows health details externally; `dev`
always shows full details; `uat` shows details only when authorized.

## Configuration reference

Every profile is driven by environment variables — no secrets are
committed. `application.properties` holds shared defaults and profile
selection; each `application-{profile}.properties` overrides per-environment
values.

### Shared (`application.properties`)

| Property | Default | Purpose |
|---|---|---|
| `spring.application.name` | `core-audit-log-service` | app name |
| `spring.profiles.active` | `${SPRING_PROFILES_ACTIVE:dev}` | active profile |
| `server.port` | `${SERVER_PORT:8080}` | HTTP port |
| `management.endpoints.web.exposure.include` | `health,info` | exposed actuator endpoints |
| `management.endpoint.health.show-details` | `when-authorized` | overridden per profile |
| `security.write-api.username` / `.password` | `${WRITE_API_USER:}` / `${WRITE_API_PASS:}` | `WRITER` credentials |
| `security.audit-api.username` / `.password` | `${AUDIT_API_USER:}` / `${AUDIT_API_PASS:}` | `AUDITOR` credentials |
| `logging.level.root` | `INFO` | overridden per profile |
| `logging.level.com.example.audit` | `INFO` | overridden per profile |
| `audit.archival.after` | `P90D` | age (ISO-8601 duration) after which records become archive-eligible |
| `audit.archival.sweep-interval` | `PT1H` | how often the archival sweep runs |

### `dev` profile

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `audit_log_dev` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `WRITE_API_USER` / `WRITE_API_PASS` | `audit_writer_dev` / `audit_writer_dev` |
| `AUDIT_API_USER` / `AUDIT_API_PASS` | `audit_reader_dev` / `audit_reader_dev` |

`spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.show-sql=true`,
`logging.level.root=DEBUG`, full actuator health details always shown.

### `uat` profile

| Variable | Default |
|---|---|
| `DB_HOST` | required |
| `DB_PORT` | `5432` |
| `DB_NAME` | `audit_log_uat` |
| `DB_USERNAME` | required |
| `DB_PASSWORD` | required |
| `WRITE_API_USER` / `WRITE_API_PASS` | required |
| `AUDIT_API_USER` / `AUDIT_API_PASS` | required |

`spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.show-sql=false`,
`logging.level.root=INFO`, health details shown only when authorized.

### `prod` profile

| Variable | Default |
|---|---|
| `DB_HOST` | required |
| `DB_PORT` | `5432` |
| `DB_NAME` | required, no default |
| `DB_USERNAME` | required |
| `DB_PASSWORD` | required |
| `WRITE_API_USER` / `WRITE_API_PASS` | required |
| `AUDIT_API_USER` / `AUDIT_API_PASS` | required |

No fallback defaults for any credential — they must come from the
deployment environment / secrets manager.
`spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.show-sql=false`,
`logging.level.root=WARN`, health details never shown externally.

### `test` profile (`src/test/resources/application-test.properties`)

In-memory H2 database in PostgreSQL compatibility mode, so the Spring
context loads without an external Postgres instance. Not used at runtime —
only by the test suite.

| Property | Value |
|---|---|
| `spring.datasource.url` | `jdbc:h2:mem:audit_log_test;MODE=PostgreSQL` |
| `spring.datasource.username` | `sa` |
| `spring.jpa.hibernate.ddl-auto` | `update` |
| `security.write-api.username` / `.password` | `audit_writer_test` / `audit_writer_test` |
| `security.audit-api.username` / `.password` | `audit_reader_test` / `audit_reader_test` |

## Docker

This Dockerfile is runtime-only: build the jar first (e.g. in CI), then build the image.

```
mvn clean package
docker build -t core-audit-log-service:local .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  core-audit-log-service:local
```

## Further reading

- `Scenario_C.md` — ambiguity-resolution process behind the compliance
  reporting endpoint.
- `audit_prototype.md` — standalone Python reference implementation of the
  hash-chain tamper-evidence mechanism.
- `task_decompostion.md` — feature breakdown by epic and implementation status.
- `ai_usage_log.md` — record of AI assistance used while building this service.
