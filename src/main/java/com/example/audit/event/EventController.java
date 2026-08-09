package com.example.audit.event;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        EventResponse response = eventService.record(request);
        return ResponseEntity
                .created(URI.create("/api/v1/events/" + response.id()))
                .body(response);
    }

    @GetMapping
    public Page<EventResponse> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean archived,
            @PageableDefault(size = 50) Pageable pageable) {
        EventQuery query = new EventQuery(actorId, resourceType, resourceId, eventType, from, to, archived);
        return eventService.search(query, pageable);
    }

    @PostMapping("/{id}/redact")
    public EventResponse redact(@PathVariable UUID id) {
        return eventService.redact(id);
    }

    @GetMapping("/export")
    public ResponseEntity<EventBundle> export(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceId) {
        EventBundle bundle = eventService.export(actorId, resourceId);
        String filename = "audit-export-" + Instant.now().toEpochMilli() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(bundle);
    }
}
