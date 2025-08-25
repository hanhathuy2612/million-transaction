package com.hnh.example.transaction_example.repository;

import com.hnh.example.transaction_example.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Find unpublished events for outbox relay
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();

    // Find unpublished events with limit (for batch processing)
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findUnpublishedEventsWithLimit(@Param("limit") int limit);

    // Mark events as published in batch
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.published = true, o.publishedAt = :publishedAt WHERE o.id IN :ids")
    void markAsPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);

    // Find events by aggregate for debugging
    @Query("SELECT o FROM OutboxEvent o WHERE o.aggregateId = :aggregateId ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByAggregateId(@Param("aggregateId") java.util.UUID aggregateId);

    // Cleanup old published events (for housekeeping)
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :cutoffDate")
    void deleteOldPublishedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Count unpublished events for monitoring
    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.published = false")
    Long countUnpublishedEvents();

    // Find events by type for analytics
    @Query("SELECT o FROM OutboxEvent o WHERE o.eventType = :eventType AND o.createdAt >= :since")
    List<OutboxEvent> findByEventTypeSince(@Param("eventType") String eventType, @Param("since") LocalDateTime since);
}
