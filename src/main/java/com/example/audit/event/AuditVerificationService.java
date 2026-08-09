package com.example.audit.event;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditVerificationService {

    private final EventRecordRepository repository;

    public AuditVerificationService(EventRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ChainVerificationResult verify() {
        List<EventRecord> records = repository.findAllInChainOrder();

        String expectedPreviousHash = EventHashing.GENESIS_HASH;
        long checked = 0;
        for (EventRecord record : records) {
            checked++;

            if (!record.getPreviousHash().equals(expectedPreviousHash)) {
                ChainViolationType violation = checked == 1
                        ? ChainViolationType.INVALID_GENESIS
                        : ChainViolationType.PREVIOUS_HASH_MISMATCH;
                return ChainVerificationResult.broken(checked, record.getId(), violation,
                        expectedPreviousHash, record.getPreviousHash());
            }

            String recomputedHash = EventHashing.contentHash(
                    record.getEventType(), record.getActorId(), record.getResourceType(),
                    record.getResourceId(), record.getPayload(), record.getTimestamp(),
                    record.getReceivedAt(), record.getPreviousHash());
            if (!recomputedHash.equals(record.getContentHash())) {
                return ChainVerificationResult.broken(checked, record.getId(),
                        ChainViolationType.CONTENT_HASH_MISMATCH, recomputedHash, record.getContentHash());
            }

            expectedPreviousHash = record.getContentHash();
        }

        return ChainVerificationResult.intact(checked);
    }
}
