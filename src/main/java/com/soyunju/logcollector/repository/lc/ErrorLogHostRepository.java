package com.soyunju.logcollector.repository.lc;

import com.soyunju.logcollector.domain.lc.ErrorLogHost;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
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

  /*  @Query(value = "SELECT COUNT(*) FROM error_log_hosts WHERE log_hash = :logHash", nativeQuery = true)
    long countHostsByLogHash(@Param("logHash") String logHash); */

    // System Draft 위해 추가
    @Query(value = """
            SELECT
              eh.log_hash AS logHash,
              CAST(COUNT(*) AS SIGNED) AS hostCount,
              CAST(COALESCE(SUM(eh.repeat_count), 0) AS SIGNED) AS repeatCount,
              MAX(eh.service_name) AS serviceName
            FROM error_log_hosts eh
            WHERE eh.log_hash IN (:logHashes)
            GROUP BY eh.log_hash
            """, nativeQuery = true)
    List<HostAgg> aggregateByLogHash(@Param("logHashes") List<String> logHashes);


    // Incident hostCount 포함 조회 (N+1 방지)
  /*  @Query(value = """
            SELECT
              log_hash AS logHash,
              COUNT(*) AS hostCount
            FROM error_log_hosts
            WHERE log_hash IN (:logHashes)
            GROUP BY log_hash
            """, nativeQuery = true)
    List<LogHashHostCountAgg> countHostsByLogHash(@Param("logHashes") List<String> logHashes);

   */

    // 영향 호스트 수 및 repeat_count Agg
    @Query(value = """
            SELECT 
                log_hash AS logHash, 
                COUNT(*) AS hostCount, 
                SUM(repeat_count) AS repeatCount, 
                MAX(service_name) AS serviceName
            FROM error_log_hosts 
            WHERE log_hash IN (:logHashes) 
            GROUP BY log_hash
            """, nativeQuery = true)
    List<com.soyunju.logcollector.repository.lc.agg.HostAgg> countHostsByLogHash(@Param("logHashes") List<String> logHashes);

    // 영향 호스트 기준 rank
    @Query(value = """
            SELECT 
                log_hash AS logHash, 
                COUNT(*) AS hostCount, 
                SUM(repeat_count) AS repeatCount, 
                MAX(service_name) AS serviceName
            FROM error_log_hosts 
            GROUP BY log_hash 
            ORDER BY hostCount DESC 
            LIMIT :limit
            """, nativeQuery = true)
    List<com.soyunju.logcollector.repository.lc.agg.HostAgg> findTopHashesByHostCount(@Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM error_log_hosts WHERE log_hash = :logHash", nativeQuery = true)
    long countHostsByLogHash(@Param("logHash") String logHash);

}