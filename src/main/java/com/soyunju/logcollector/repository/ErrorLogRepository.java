package com.soyunju.logcollector.repository;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    // 서비스 네임과 관계없이 특정 상태(예: NEW)인 로그만 페이징 조회
    Page<ErrorLog> findByStatus(ErrorStatus status, Pageable pageable);

    Page<ErrorLog> findByServiceNameAndStatus(String serviceName, ErrorStatus status, Pageable pageable);

    Page<ErrorLog> findByServiceNameOrderByOccurrenceTimeDesc(String serviceName, Pageable pageable);

    Page<ErrorLog> findByOccurrenceTimeAfterOrderByOccurrenceTimeDesc(LocalDateTime time, Pageable pageable);

    // AI 분석 전용: 특정 필드만 조회하여 DB 부하 최적화
    @Query("SELECT e.errorCode as errorCode, e.summary as summary, e.message as message " +
            "FROM ErrorLog e WHERE e.id = :id")
    Optional<LogAnalysisData> findAnalysisDataById(@Param("id") Long id);

    interface LogAnalysisData {
        String getErrorCode();

        String getSummary();

        String getMessage();
    }

    // 260112_sy log hashing and db upsert
    // 260112_sy upsert 이후 단건 조회용
    Optional<ErrorLog> findByLogHash(String logHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO error_logs (
              service_name, host_name, log_level,
              message, stack_trace, error_code, summary,
              log_hash, repeat_count, status, analysis_status,
              occurrence_time, last_occurrence_time
            )
            VALUES (
              :serviceName, :hostName, :logLevel,
              :message, :stackTrace, :errorCode, :summary,
              :logHash, 1, 'NEW', 'PENDING',
              :now, :now
            )
            ON DUPLICATE KEY UPDATE
              repeat_count = repeat_count + 1,
              last_occurrence_time = VALUES(last_occurrence_time),
              status = 'NEW',
              analysis_status = 'PENDING'
            """, nativeQuery = true)
    int upsertIncident(
            @Param("serviceName") String serviceName,
            @Param("hostName") String hostName,
            @Param("logLevel") String logLevel,
            @Param("message") String message,
            @Param("stackTrace") String stackTrace,
            @Param("errorCode") String errorCode,
            @Param("summary") String summary,
            @Param("logHash") String logHash,
            @Param("now") LocalDateTime now
    );


}