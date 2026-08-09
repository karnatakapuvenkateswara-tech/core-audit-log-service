package com.example.audit.event;

enum ChainViolationType {
    /** The record's stored contentHash does not match a hash recomputed from its stored fields. */
    CONTENT_HASH_MISMATCH,
    /**
     * The record is not marked redacted, but its live payload no longer hashes to the
     * stored payloadHash - the payload was modified outside the redaction path.
     */
    PAYLOAD_HASH_MISMATCH,
    /** The record's previousHash does not match the preceding record's contentHash. */
    PREVIOUS_HASH_MISMATCH,
    /** The first record in the chain does not point back to the genesis hash. */
    INVALID_GENESIS
}
