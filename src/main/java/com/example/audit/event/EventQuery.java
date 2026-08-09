package com.example.audit.event;

import java.time.Instant;

record EventQuery(
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to,
        Boolean archived
) {
}
