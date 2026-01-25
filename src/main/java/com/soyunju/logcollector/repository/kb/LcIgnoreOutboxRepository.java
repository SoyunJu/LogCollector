package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.LcIgnoreOutbox;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LcIgnoreOutboxRepository extends JpaRepository<LcIgnoreOutbox, Long> {

    @Query("""
        SELECT o FROM LcIgnoreOutbox o
        WHERE o.status IN ('PENDING','FAILED')
          AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
        ORDER BY o.createdAt ASC
    """)
    List<LcIgnoreOutbox> findProcessTargets(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE LcIgnoreOutbox o
        SET o.status = :toStatus,
            o.attemptCount = :attemptCount,
            o.nextRetryAt = :nextRetryAt,
            o.lastError = :lastError
        WHERE o.id = :id
    """)
    int updateState(
            @Param("id") Long id,
            @Param("toStatus") LcIgnoreOutboxStatus toStatus,
            @Param("attemptCount") int attemptCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError
    );

}
