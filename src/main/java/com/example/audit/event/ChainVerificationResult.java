package com.example.audit.event;

import java.util.UUID;

record ChainVerificationResult(
        boolean intact,
        long recordsChecked,
        UUID brokenRecordId,
        ChainViolationType violationType,
        String expectedHash,
        String actualHash
) {
    static ChainVerificationResult intact(long recordsChecked) {
        return new ChainVerificationResult(true, recordsChecked, null, null, null, null);
    }

    static ChainVerificationResult broken(long recordsChecked, UUID brokenRecordId,
                                           ChainViolationType violationType,
                                           String expectedHash, String actualHash) {
        return new ChainVerificationResult(false, recordsChecked, brokenRecordId, violationType, expectedHash, actualHash);
    }
}
