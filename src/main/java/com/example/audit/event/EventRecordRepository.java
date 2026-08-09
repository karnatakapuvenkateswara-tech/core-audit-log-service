package com.example.audit.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import jakarta.persistence.LockModeType;

/**
 * Deliberately narrow: extends the base {@link Repository} marker rather than
 * JpaRepository/CrudRepository so no update or delete operation is exposed.
 * The log is append-only end to end.
 */
public interface EventRecordRepository extends Repository<EventRecord, UUID> {

    EventRecord save(EventRecord eventRecord);

    /**
     * Locks the chain tail for the duration of the caller's transaction so concurrent
     * writers cannot both compute a new record's {@code previousHash} from the same
     * predecessor. Must only be called within a write transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventRecord e ORDER BY e.receivedAt DESC, e.id DESC LIMIT 1")
    Optional<EventRecord> findChainTailForUpdate();

    @Query("SELECT e FROM EventRecord e ORDER BY e.receivedAt ASC, e.id ASC")
    List<EventRecord> findAllInChainOrder();

    @Query("""
            SELECT e FROM EventRecord e
            WHERE (:actorId IS NULL OR e.actorId = :actorId)
            AND (:resourceType IS NULL OR e.resourceType = :resourceType)
            AND (:resourceId IS NULL OR e.resourceId = :resourceId)
            AND (:eventType IS NULL OR e.eventType = :eventType)
            AND (:from IS NULL OR e.timestamp >= :from)
            AND (:to IS NULL OR e.timestamp <= :to)
            """)
    Page<EventRecord> search(
            @Param("actorId") @Nullable String actorId,
            @Param("resourceType") @Nullable String resourceType,
            @Param("resourceId") @Nullable String resourceId,
            @Param("eventType") @Nullable String eventType,
            @Param("from") @Nullable Instant from,
            @Param("to") @Nullable Instant to,
            Pageable pageable);
}
