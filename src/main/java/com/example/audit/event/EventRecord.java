package com.example.audit.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "event_records")
public class EventRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String actorId;

    @Column(nullable = false, updatable = false)
    private String resourceType;

    @Column(nullable = false, updatable = false)
    private String resourceId;
//added columnDefination as text removed @LOB fixes
    @Column(columnDefinition = "text", nullable = false, updatable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String contentHash;

    protected EventRecord() {
        // JPA
    }

    public EventRecord(String eventType, String actorId, String resourceType,
                        String resourceId, String payload, Instant timestamp,
                        String previousHash) {
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.timestamp = timestamp;
        // Truncated to microseconds: standard SQL TIMESTAMP precision (H2 and Postgres),
        // so the hash computed here matches what a later read-back will recompute.
        this.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.previousHash = previousHash;
        this.contentHash = EventHashing.contentHash(
                eventType, actorId, resourceType, resourceId, payload, timestamp, receivedAt, previousHash);
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getContentHash() {
        return contentHash;
    }
}
