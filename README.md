# core-audit-log-service

Skeleton Spring Boot 3 (Java 21) Maven service for the Core Audit Log, with
`dev` / `uat` / `prod` configuration profiles and a runtime Docker image.

## Layout

```
src/main/java/com/example/audit/CoreAuditLogServiceApplication.java
src/main/resources/application.properties        # shared defaults, picks active profile
src/main/resources/application-dev.properties
src/main/resources/application-uat.properties
src/main/resources/application-prod.properties
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

## Configuration

Each profile is driven by environment variables — no secrets are committed:

| Variable        | dev default          | uat / prod            |
|-----------------|-----------------------|------------------------|
| `DB_HOST`       | `localhost`            | required                |
| `DB_PORT`       | `5432`                 | `5432`                  |
| `DB_NAME`       | `audit_log_dev`        | required (uat has a default) |
| `DB_USERNAME`   | `audit_dev`             | required                |
| `DB_PASSWORD`   | `audit_dev`             | required                |
| `SERVER_PORT`   | `8080`                  | `8080`                  |
| `WRITE_API_USER` | `audit_writer_dev`    | required                |
| `WRITE_API_PASS` | `audit_writer_dev`    | required                |

`prod` has no fallback defaults for credentials — they must come from the
deployment environment / secrets manager.

## Write API

`POST /api/v1/events` accepts a single event record and appends it to the
log. The endpoint requires HTTP Basic auth (`WRITE_API_USER` /
`WRITE_API_PASS`); all other requests are rejected with `401`.

Request body:

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

`eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, and
`timestamp` are all required. A successful write returns `201 Created` with
the stored record, including a generated `id` and server-side `receivedAt`.

There is no update or delete endpoint for events, and the JPA repository
(`EventRecordRepository`) only exposes `save` — the log is append-only by
construction, not just by convention.

```
curl -u audit_writer_dev:audit_writer_dev \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user-123","resourceType":"SESSION","resourceId":"session-456","payload":{"ip":"10.0.0.1"},"timestamp":"2026-08-08T12:00:00Z"}' \
  http://localhost:8080/api/v1/events
```

## Docker

This Dockerfile is runtime-only: build the jar first (e.g. in CI), then build the image.

```
mvn clean package
docker build -t core-audit-log-service:local .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  core-audit-log-service:local
```

## Actuator

Health and info endpoints are exposed at `/actuator/health` and `/actuator/info`.
`prod` never shows health details externally; `dev` shows full details.
