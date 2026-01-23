package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, IncidentRepositoryCustom {

    Optional<Incident> findById(Long id);
    Optional<Incident> findByLogHash(String logHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incident i WHERE i.logHash = :logHash")
    Optional<Incident> findByLogHashWithLock(@Param("logHash") String logHash);

    // 추가: DraftPolicyService에서 대량 조회를 위해 필요함
    // N + 1 방지
    List<Incident> findAllByLogHashIn(java.util.Set<String> logHashes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(transactionManager = "kbTransactionManager")
    @Query(value = """
    INSERT INTO incident (
        log_hash, service_name, summary, stack_trace, error_code,
        error_level, status, first_occurred_at, last_occurred_at, repeat_count
    ) VALUES (
        :logHash, :serviceName, :summary, :stackTrace, :errorCode,
        :errorLevel, 'OPEN', :ts, :ts, 1
    ) ON DUPLICATE KEY UPDATE
        repeat_count = repeat_count + 1,
        last_occurred_at = VALUES(last_occurred_at),

        -- summary는 DB값이 NULL/blank일 때만 채움
        summary = CASE
            WHEN summary IS NULL OR TRIM(summary) = '' THEN VALUES(summary)
            ELSE summary
        END,

        status = CASE WHEN status = 'RESOLVED' THEN 'OPEN' ELSE status END,
        resolved_at = CASE WHEN status = 'RESOLVED' THEN NULL ELSE resolved_at END
    """, nativeQuery = true)
    void upsertIncident(
            @Param("logHash") String logHash,
            @Param("serviceName") String serviceName,
            @Param("summary") String summary,
            @Param("stackTrace") String stackTrace,
            @Param("errorCode") String errorCode,
            @Param("errorLevel") String errorLevel,
            @Param("ts") LocalDateTime ts
    );

    // Not RESOLVED incident
    @Query("SELECT i.logHash FROM Incident i WHERE i.status <> :status")
    List<String> findNotResolvedLogHash(@Param("status") IncidentStatus status);

}
