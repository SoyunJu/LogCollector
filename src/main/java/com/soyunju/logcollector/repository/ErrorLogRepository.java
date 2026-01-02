package com.soyunju.logcollector.repository;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    // 서비스 네임과 관계없이 특정 상태(예: NEW)인 로그만 페이징 조회
    Page<ErrorLog> findByStatus(ErrorStatus status, Pageable pageable);

    // 기존 메서드들도 Pageable을 적용하여 유지합니다.
    Page<ErrorLog> findByServiceNameAndStatus(String serviceName, ErrorStatus status, Pageable pageable);

    Page<ErrorLog> findByServiceNameOrderByOccurrenceTimeDesc(String serviceName, Pageable pageable);

    Page<ErrorLog> findByOccurrenceTimeAfterOrderByOccurrenceTimeDesc(LocalDateTime time, Pageable pageable);
}