package com.example.audit.event;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {

    private final EventService eventService;

    public ComplianceController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/access-report")
    public List<EventResponse> accessReport(
            @RequestParam String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return eventService.accessReport(resourceId, eventType, from, to);
    }
}
