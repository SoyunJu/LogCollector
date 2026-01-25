package com.soyunju.logcollector.repository.lc;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.kb.LogAnalysisData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    // 1. 특정 상태인 로그 페이징 조회
    Page<ErrorLog> findByStatus(ErrorStatus status, Pageable pageable);

    // 2. 서비스명 + 상태 조회
    Page<ErrorLog> findByServiceNameAndStatus(String serviceName, ErrorStatus status, Pageable pageable);

    // 3. 서비스명 기준 정렬 조회
    // OrderBy 앞의 필드명(ServiceName)과 뒤의 필드명(OccurredTime)을 대문자로 구분
    Page<ErrorLog> findByServiceNameOrderByOccurredTimeDesc(String serviceName, Pageable pageable);

    // 4. 특정 시간 이후 정렬 조회
    Page<ErrorLog> findByOccurredTimeAfterOrderByOccurredTimeDesc(LocalDateTime time, Pageable pageable);

    // 5. 서비스명 조회
    Page<ErrorLog> findByServiceName(String serviceName, Pageable pageable);

    // 6. 특정 시간 이후 조회
    Page<ErrorLog> findByOccurredTimeAfter(LocalDateTime time, Pageable pageable);

    // 7. 영향 호스트 Count 추가
    @Query("SELECT COUNT(h) FROM ErrorLogHost h WHERE h.logHash = :logHash")
    long countDistinctHostsByLogHash(@Param("logHash") String logHash);

    // AI 분석 전용: 특정 필드만 조회하여 DB 부하 최적화 (DTO로 분리)
    @Query("SELECT new com.soyunju.logcollector.dto.kb.LogAnalysisData(e.errorCode, e.summary, e.message) " +
            "FROM ErrorLog e WHERE e.id = :id")
    Optional<LogAnalysisData> findAnalysisDataById(@Param("id") Long id);

    Optional<ErrorLog> findByLogHash(String logHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO error_logs (
            service_name, host_name, log_level, message, stack_trace, 
            occurred_time, first_occurred_time, last_occurred_time, 
            status, error_code, summary, log_hash, repeat_count,
            created_at, updated_at
        ) VALUES (
            :serviceName, :hostName, :logLevel, :message, :stackTrace,
            :occurredTime, :occurredTime, :occurredTime,
            'NEW', :errorCode, :summary, :logHash, 1,
            NOW(), NOW()
        ) ON DUPLICATE KEY UPDATE
            repeat_count = repeat_count + 1,
            last_occurred_time = VALUES(last_occurred_time),
            occurred_time = VALUES(occurred_time),
            updated_at = NOW(),
            status = CASE WHEN status = 'RESOLVED' THEN 'NEW' ELSE status END,
            resolved_at = CASE WHEN status = 'RESOLVED' THEN NULL ELSE resolved_at END,
        """, nativeQuery = true)
    int upsertErrorLog(
            @Param("serviceName") String serviceName,
            @Param("hostName") String hostName,
            @Param("logLevel") String logLevel,
            @Param("message") String message,
            @Param("stackTrace") String stackTrace,
            @Param("occurredTime") LocalDateTime occurredTime,
            @Param("errorCode") String errorCode,
            @Param("summary") String summary,
            @Param("logHash") String logHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ErrorLog e SET e.status = com.soyunju.logcollector.domain.lc.ErrorStatus.IGNORED WHERE e.logHash = :logHash")
    int markIgnoredByLogHash(@Param("logHash") String logHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ErrorLog e SET e.status = com.soyunju.logcollector.domain.lc.ErrorStatus.NEW WHERE e.logHash = :logHash")
    int unmarkIgnoredByLogHash(@Param("logHash") String logHash);

    // 테스트 데이터 삭제용
    @Transactional
    @Modifying
    @Query("DELETE FROM ErrorLog e WHERE e.logHash = :logHash")
    void deleteByLogHash(@Param("logHash") String logHash);


}
