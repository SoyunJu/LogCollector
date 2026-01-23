package com.soyunju.logcollector.service.scheduler;

import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.service.kb.autopolicy.DraftPolicyService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbScheduler {

    private final DraftPolicyService draftPolicyService;
    private final KbArticleRepository kbArticleRepository;
    private final KbDraftService kbDraftService;

   /*  @Scheduled(cron = "0 0/30 * * * *")
    public void runAutoDrafting() {
        DraftPolicyService.DraftRunResult r = draftPolicyService.runAutoDraft();
        log.info("[SCHED][AUTO_DRAFT] created={} failed={} skippedExists={} skippedNoIncident={} skippedNotMatched={}",
                r.created(), r.failed(), r.skippedExists(), r.skippedNoIncident(), r.skippedNotMatched());
    } */

    @Scheduled(cron = "0 10 3 * * *")
    public void promoteDefinite() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);

        kbArticleRepository.bulkPromoteDefiniteByLastActivity(
                KbStatus.DEFINITE,
                5,
              //  List.of(KbStatus.OPEN, KbStatus.UNDERWAY, KbStatus.RESPONDED),
                List.of(KbStatus.RESPONDED),
                cutoff
        );
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduleDraftCleanup() {
        kbDraftService.cleanupExpiredDrafts();
    }
}
