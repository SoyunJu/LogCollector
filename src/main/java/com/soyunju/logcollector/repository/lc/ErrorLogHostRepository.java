package com.soyunju.logcollector.repository.lc;

import com.soyunju.logcollector.domain.lc.ErrorLogHost;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
import com.soyunju.logcollector.repository.lc.agg.LogHashHostCountAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    // System Draft 위해 추가
    // 집계 제외 조건:
    // - incident.status = RESOLVED
    @Query(value = """
            SELECT
              i.log_hash AS logHash,
              COUNT(*) AS hostCount,
              COALESCE(SUM(eh.repeat_count), 0) AS repeatCount,
              MAX(eh.service_name) AS serviceName
            FROM incident i
            JOIN error_log_hosts eh
              ON eh.log_hash = i.log_hash
            WHERE i.status <> 'RESOLVED'
            GROUP BY i.log_hash
            """, nativeQuery = true)
    List<HostAgg> aggregateByLogHash();

    // Incident hostCount 포함 조회 (N+1 방지)
    @Query(value = """
            SELECT
              log_hash AS logHash,
              COUNT(*) AS hostCount
            FROM error_log_hosts
            WHERE log_hash IN (:logHashes)
            GROUP BY log_hash
            """, nativeQuery = true)
    List<LogHashHostCountAgg> countHostsByLogHashIn(@Param("logHashes") List<String> logHashes);

}