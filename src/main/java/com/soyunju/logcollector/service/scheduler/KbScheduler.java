package com.soyunju.logcollector.service.scheduler;

import com.soyunju.logcollector.service.kb.crud.IncidentService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.kb.crud.LcIgnoreOutboxProcessorService;
import com.soyunju.logcollector.service.lc.ignore.LcIgnoreApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbScheduler {

    private final KbDraftService kbDraftService;
    private final LcIgnoreApplyService lcIgnoreApplyService;
    private final IncidentService incidentService;
    private final LcIgnoreOutboxProcessorService lcIgnoreOutboxProcessorService;

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduleDraftCleanup() {
        kbDraftService.cleanupExpiredDrafts();
    }

    // ===== IGNORED outbox processor (eventual consistency + retry) =====
    @Scheduled(fixedDelay = 1000)
    public void syncIgnoredLogsToLc() {
        lcIgnoreOutboxProcessorService.process(LocalDateTime.now());
    }

    // Incident Close
    @Scheduled(cron = "0 */1 * * * *")
    @Transactional(transactionManager = "kbTransactionManager")
    public void autoCloseResolvedIncidents() {
        try {
            log.info("[SCHED][AUTO_CLOSE][ENTER]");
            int closed = incidentService.autoCloseIncidents(LocalDateTime.now());
            log.info("[SCHED][AUTO_CLOSE][DONE] closed={}", closed);
        } catch (Exception e) {
            log.error("[SCHED][AUTO_CLOSE][FAIL]", e);
            throw e;
        }
    }



}
