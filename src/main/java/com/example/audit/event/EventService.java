package com.example.audit.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EventService {

    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;

    public EventService(EventRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Page<EventResponse> search(EventQuery query, Pageable pageable) {
        if (query.resourceId() != null && query.resourceType() == null) {
            throw new IllegalArgumentException("resourceType is required when resourceId is provided");
        }
        return repository.search(
                query.actorId(),
                query.resourceType(),
                query.resourceId(),
                query.eventType(),
                query.from(),
                query.to(),
                pageable
        ).map(EventResponse::from);
    }

    @Transactional
    public EventResponse record(EventRequest request) {
        String payloadJson = writePayload(request);
        String previousHash = repository.findChainTailForUpdate()
                .map(EventRecord::getContentHash)
                .orElse(EventHashing.GENESIS_HASH);
        EventRecord saved = repository.save(new EventRecord(
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                payloadJson,
                request.timestamp(),
                previousHash
        ));
        return EventResponse.from(saved);
    }

    private String writePayload(EventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
