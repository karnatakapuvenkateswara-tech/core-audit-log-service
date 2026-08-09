package com.example.audit.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit.archival")
public record ArchivalProperties(Duration after, Duration sweepInterval) {
}
