package com.soyunju.logcollector.repository;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.LogAnalysisData;
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
    @Query("SELECT new com.soyunju.logcollector.dto.LogAnalysisData(e.errorCode, e.summary, e.message) " +
            "FROM ErrorLog e WHERE e.id = :id")
    Optional<LogAnalysisData> findAnalysisDataById(@Param("id") Long id);

    Optional<ErrorLog> findByLogHash(String logHash);
}
