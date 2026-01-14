package com.soyunju.logcollector.service.crd;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ErrorLogMaintenanceJob {

    @PersistenceContext
    private EntityManager em;

    // 매일 03:00 실행
    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOrphanHostRows() {
        int deleted = em.createNativeQuery("""
            DELETE h
            FROM error_log_hosts h
            LEFT JOIN error_logs e ON e.log_hash = h.log_hash
            WHERE e.log_hash IS NULL
        """).executeUpdate();

        if (deleted > 0) {
            log.info("cleanupOrphanHostRows deleted={}", deleted);
        }
    }
}
