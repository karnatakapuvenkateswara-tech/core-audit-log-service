package com.example.audit.event;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventArchivalService {

    private static final Logger log = LoggerFactory.getLogger(EventArchivalService.class);

    private final EventRecordRepository repository;
    private final ArchivalProperties properties;

    public EventArchivalService(EventRecordRepository repository, ArchivalProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${audit.archival.sweep-interval}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(properties.after());
        int archived = repository.archiveEligibleRecords(cutoff, Instant.now());
        if (archived > 0) {
            log.info("Archived {} event record(s) received before {}", archived, cutoff);
        }
    }
}
