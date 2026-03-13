package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbEventOutbox;
import com.soyunju.logcollector.domain.kb.enums.KbEventOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KbEventOutboxRepository extends JpaRepository<KbEventOutbox, Long> {

    @Query("""
        SELECT o FROM KbEventOutbox o
        WHERE o.status IN ('PENDING', 'FAILED')
          AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
        ORDER BY o.createdAt ASC
    """)
    List<KbEventOutbox> findRetryTargets(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE KbEventOutbox o
        SET o.status = :status,
            o.attemptCount = :attemptCount,
            o.nextRetryAt = :nextRetryAt,
            o.lastError = :lastError
        WHERE o.id = :id
    """)
    int updateState(
            @Param("id") Long id,
            @Param("status") KbEventOutboxStatus status,
            @Param("attemptCount") int attemptCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError
    );
}