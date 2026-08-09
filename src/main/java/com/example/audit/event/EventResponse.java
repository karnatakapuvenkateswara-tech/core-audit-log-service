package com.example.audit.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        String payload,
        Instant timestamp,
        Instant receivedAt,
        String previousHash,
        String contentHash
) {
    public static EventResponse from(EventRecord record) {
        return new EventResponse(
                record.getId(),
                record.getEventType(),
                record.getActorId(),
                record.getResourceType(),
                record.getResourceId(),
                record.getPayload(),
                record.getTimestamp(),
                record.getReceivedAt(),
                record.getPreviousHash(),
                record.getContentHash()
        );
    }
}
