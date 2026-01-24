package com.soyunju.logcollector.service.scheduler;

import com.soyunju.logcollector.domain.kb.LcIgnoreOutbox;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxAction;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxStatus;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.kb.LcIgnoreOutboxRepository;
import com.soyunju.logcollector.service.kb.autopolicy.DraftPolicyService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.lc.crd.LcIgnoreApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbScheduler {

    private final DraftPolicyService draftPolicyService;
    private final KbArticleRepository kbArticleRepository;
    private final KbDraftService kbDraftService;

    // ===== IGNORED outbox 처리용 =====
    private final LcIgnoreOutboxRepository lcIgnoreOutboxRepository;
    private final LcIgnoreApplyService lcIgnoreApplyService;

   /*  @Scheduled(cron = "0 0/30 * * * *")
    public void runAutoDrafting() {
        DraftPolicyService.DraftRunResult r = draftPolicyService.runAutoDraft();
        log.info("[SCHED][AUTO_DRAFT] created={} failed={} skippedExists={} skippedNoIncident={} skippedNotMatched={}",
                r.created(), r.failed(), r.skippedExists(), r.skippedNoIncident(), r.skippedNotMatched());
    } */

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduleDraftCleanup() {
        kbDraftService.cleanupExpiredDrafts();
    }

    // ===== IGNORED outbox processor (선택 A: eventual consistency + retry) =====
    @Scheduled(cron = "0 */1 * * * *") // 1분 주기
    @Transactional(transactionManager = "kbTransactionManager")
    public void processLcIgnoreOutbox() {
        List<LcIgnoreOutbox> targets = lcIgnoreOutboxRepository.findProcessTargets(
                LocalDateTime.now(),
                PageRequest.of(0, 50)
        );

        if (targets.isEmpty()) {
            return;
        }

        for (LcIgnoreOutbox o : targets) {
            // PROCESSING 마킹
            lcIgnoreOutboxRepository.updateState(
                    o.getId(),
                    LcIgnoreOutboxStatus.PROCESSING,
                    o.getAttemptCount(),
                    o.getNextRetryAt(),
                    o.getLastError()
            );

            try {
                if (o.getAction() == LcIgnoreOutboxAction.IGNORE) {
                    lcIgnoreApplyService.applyIgnore(o.getLogHash());
                } else {
                    lcIgnoreApplyService.applyUnignore(o.getLogHash());
                }

                lcIgnoreOutboxRepository.updateState(
                        o.getId(),
                        LcIgnoreOutboxStatus.SUCCESS,
                        o.getAttemptCount(),
                        null,
                        null
                );
            } catch (Exception e) {
                int nextAttempt = o.getAttemptCount() + 1;

                // backoff (v1 단순형): 1m, 5m, 30m, 2h
                LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(
                        switch (Math.min(nextAttempt, 4)) {
                            case 1 -> 1;
                            case 2 -> 5;
                            case 3 -> 30;
                            default -> 120;
                        }
                );

                lcIgnoreOutboxRepository.updateState(
                        o.getId(),
                        LcIgnoreOutboxStatus.FAILED,
                        nextAttempt,
                        nextRetry,
                        e.getMessage()
                );

                log.warn("[SCHED][LC_IGNORE_OUTBOX] failed id={} hash={} action={} attempt={} nextRetryAt={} err={}",
                        o.getId(), o.getLogHash(), o.getAction(), nextAttempt, nextRetry, e.getMessage());
            }
        }
    }
}
