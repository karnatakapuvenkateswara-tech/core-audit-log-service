package com.example.audit.event;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

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
                query.archived(),
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

    @Transactional(readOnly = true)
    public EventBundle export(String actorId, String resourceId) {
        if (actorId == null && resourceId == null) {
            throw new IllegalArgumentException("At least one of actorId or resourceId is required for export");
        }

        List<EventRecord> records = repository.findForExport(actorId, resourceId);
        List<EventResponse> responses = records.stream().map(EventResponse::from).toList();
        String bundleHash = EventHashing.bundleHash(records.stream().map(EventRecord::getContentHash).toList());

        EventBundleManifest manifest = new EventBundleManifest(
                Instant.now(), actorId, resourceId, records.size(), bundleHash);
        return new EventBundle(manifest, responses);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> accessReport(String resourceId, String eventType, Instant from, Instant to) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId is required for a compliance access report");
        }
        return repository.findAccessTrail(resourceId, eventType, from, to).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional
    public EventResponse redact(UUID id) {
        EventRecord record = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No event record with id " + id));
        record.redact(Instant.now());
        return EventResponse.from(record);
    }

    private String writePayload(EventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
