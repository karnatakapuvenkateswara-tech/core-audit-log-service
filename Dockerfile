FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

ARG JAR_FILE=target/core-audit-log-service.jar
COPY ${JAR_FILE} app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
