package com.example.audit.event;

import java.time.Instant;

/**
 * Describes an {@link EventBundle} and lets a verifier confirm the bundle file
 * itself wasn't reordered, truncated, or altered after export. This is separate
 * from per-record verification: bundleHash covers the sequence of exported
 * records' contentHash values, not the chain those records came from.
 */
record EventBundleManifest(
        Instant exportedAt,
        String actorId,
        String resourceId,
        int recordCount,
        String bundleHash
) {
}
