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

            String recomputedContentHash = EventHashing.contentHash(
                    record.getEventType(), record.getActorId(), record.getResourceType(),
                    record.getResourceId(), record.getPayloadHash(), record.getTimestamp(),
                    record.getReceivedAt(), record.getPreviousHash());
            if (!recomputedContentHash.equals(record.getContentHash())) {
                return ChainVerificationResult.broken(checked, record.getId(),
                        ChainViolationType.CONTENT_HASH_MISMATCH, recomputedContentHash, record.getContentHash());
            }

            // A redacted record's payload was deliberately replaced with a tombstone, so
            // it will never hash back to payloadHash - that's expected, not a violation.
            // For everything else, the live payload must still match what was committed
            // to at write time.
            if (!record.isRedacted()) {
                String recomputedPayloadHash = EventHashing.payloadHash(record.getPayload());
                if (!recomputedPayloadHash.equals(record.getPayloadHash())) {
                    return ChainVerificationResult.broken(checked, record.getId(),
                            ChainViolationType.PAYLOAD_HASH_MISMATCH, recomputedPayloadHash, record.getPayloadHash());
                }
            }

            expectedPreviousHash = record.getContentHash();
        }

        return ChainVerificationResult.intact(checked);
    }
}
