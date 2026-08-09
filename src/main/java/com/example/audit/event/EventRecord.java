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
    @Column(columnDefinition = "text", nullable = false)
    private String payload;

    /**
     * SHA-256 of the original payload, captured once at write time. {@code contentHash}
     * commits to this rather than to {@code payload} directly, so redacting the payload
     * later never invalidates the chain - see {@link EventHashing}.
     */
    @Column(nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String contentHash;

    /**
     * Lifecycle metadata, not chain content: mutable by design (set by the archival
     * sweep after the record is written) and deliberately excluded from
     * {@link EventHashing#contentHash}, so archiving a record never invalidates
     * its hash or breaks the chain for records around it.
     */
    @Column(nullable = false)
    private boolean archived = false;

    @Column
    private Instant archivedAt;

    /**
     * Payload lifecycle metadata, mutable by design and excluded from the hash for
     * the same reason as {@code archived}: it records what happened to the record
     * after the fact, not what the record originally was.
     */
    @Column(nullable = false)
    private boolean redacted = false;

    @Column
    private Instant redactedAt;

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
        this.payloadHash = EventHashing.payloadHash(payload);
        this.timestamp = timestamp;
        // Truncated to microseconds: standard SQL TIMESTAMP precision (H2 and Postgres),
        // so the hash computed here matches what a later read-back will recompute.
        this.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.previousHash = previousHash;
        this.contentHash = EventHashing.contentHash(
                eventType, actorId, resourceType, resourceId, payloadHash, timestamp, receivedAt, previousHash);
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

    public String getPayloadHash() {
        return payloadHash;
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

    public boolean isArchived() {
        return archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isRedacted() {
        return redacted;
    }

    public Instant getRedactedAt() {
        return redactedAt;
    }

    /**
     * Replaces the payload with a tombstone. {@code payloadHash} and {@code contentHash}
     * are untouched, so the chain stays valid; verification of this record's payload
     * against payloadHash is simply no longer possible, by design.
     */
    void redact(Instant redactedAt) {
        if (this.redacted) {
            throw new IllegalStateException("Record " + id + " is already redacted");
        }
        this.payload = "[REDACTED]";
        this.redacted = true;
        this.redactedAt = redactedAt;
    }
}
