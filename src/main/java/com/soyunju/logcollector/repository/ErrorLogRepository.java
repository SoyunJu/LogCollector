package com.soyunju.logcollector.repository;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // 가장 최근에 발생한 동일 해시 로그 조회
    Optional<ErrorLog> findFirstByLogHashOrderByOccurrenceTimeDesc(String logHash);

}