package com.example.audit.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import jakarta.persistence.LockModeType;

/**
 * Deliberately narrow: extends the base {@link Repository} marker rather than
 * JpaRepository/CrudRepository so no general update or delete operation is exposed.
 * The log is append-only end to end; the one exception is {@link #archiveEligibleRecords},
 * a bulk flag flip that touches only lifecycle metadata, never chain content.
 */
public interface EventRecordRepository extends Repository<EventRecord, UUID> {

    EventRecord save(EventRecord eventRecord);

    Optional<EventRecord> findById(UUID id);

    /**
     * Marks records older than {@code cutoff} (by receivedAt) as archived. Only
     * {@code archived}/{@code archivedAt} are touched - eventType, payload, hashes,
     * and every other chain-content column stay untouched, so this never affects
     * hash-chain verification.
     */
    @Modifying
    @Query("""
            UPDATE EventRecord e
            SET e.archived = true, e.archivedAt = :archivedAt
            WHERE e.archived = false AND e.receivedAt < :cutoff
            """)
    int archiveEligibleRecords(@Param("cutoff") Instant cutoff, @Param("archivedAt") Instant archivedAt);

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
            AND (:resourceId IS NULL OR e.resourceId = :resourceId)
            ORDER BY e.receivedAt ASC, e.id ASC
            """)
    List<EventRecord> findForExport(@Param("actorId") @Nullable String actorId,
                                     @Param("resourceId") @Nullable String resourceId);

    @Query("""
            SELECT e FROM EventRecord e
            WHERE (:actorId IS NULL OR e.actorId = :actorId)
            AND (:resourceType IS NULL OR e.resourceType = :resourceType)
            AND (:resourceId IS NULL OR e.resourceId = :resourceId)
            AND (:eventType IS NULL OR e.eventType = :eventType)
            AND (:from IS NULL OR e.timestamp >= :from)
            AND (:to IS NULL OR e.timestamp <= :to)
            AND (:archived IS NULL OR e.archived = :archived)
            """)
    Page<EventRecord> search(
            @Param("actorId") @Nullable String actorId,
            @Param("resourceType") @Nullable String resourceType,
            @Param("resourceId") @Nullable String resourceId,
            @Param("eventType") @Nullable String eventType,
            @Param("from") @Nullable Instant from,
            @Param("to") @Nullable Instant to,
            @Param("archived") @Nullable Boolean archived,
            Pageable pageable);

    /**
     * Chronological (by event {@code timestamp}, not ingestion order) access trail for a
     * single resource, for compliance/regulator reporting. Always returns every matching
     * record regardless of archived status - an archived record is still evidence of access.
     */
    @Query("""
            SELECT e FROM EventRecord e
            WHERE e.resourceId = :resourceId
            AND (:eventType IS NULL OR e.eventType = :eventType)
            AND (:from IS NULL OR e.timestamp >= :from)
            AND (:to IS NULL OR e.timestamp <= :to)
            ORDER BY e.timestamp ASC, e.id ASC
            """)
    List<EventRecord> findAccessTrail(
            @Param("resourceId") String resourceId,
            @Param("eventType") @Nullable String eventType,
            @Param("from") @Nullable Instant from,
            @Param("to") @Nullable Instant to);
}
