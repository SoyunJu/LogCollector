package com.soyunju.logcollector.repository;

import com.soyunju.logcollector.domain.ErrorLogHost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ErrorLogHostRepository extends JpaRepository<ErrorLogHost, Long> {

    // host별 upsert (DB 컬럼명: first_occurrence_time, last_occurrence_time 반영)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO error_log_hosts (
          log_hash, service_name, host_name, ip,
          first_occurrence_time, last_occurrence_time, repeat_count
        )
        VALUES (
          :logHash, :serviceName, :hostName, :ip,
          :now, :now, 1
        )
        ON DUPLICATE KEY UPDATE
          repeat_count = repeat_count + 1,
          last_occurrence_time = VALUES(last_occurrence_time),
          ip = VALUES(ip)
        """, nativeQuery = true)
    int upsertHostCounter(
            @Param("logHash") String logHash,
            @Param("serviceName") String serviceName,
            @Param("hostName") String hostName,
            @Param("ip") String ip,
            @Param("now") LocalDateTime now
    );

    @Query(value = "SELECT COUNT(*) FROM error_log_hosts WHERE log_hash = :logHash", nativeQuery = true)
    long countHostsByLogHash(@Param("logHash") String logHash);
}