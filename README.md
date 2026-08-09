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

`prod` has no fallback defaults for credentials — they must come from the
deployment environment / secrets manager.

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
