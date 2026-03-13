package com.soyunju.logcollector.service.kb.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soyunju.logcollector.domain.kb.KbEventOutbox;
import com.soyunju.logcollector.domain.kb.enums.KbEventOutboxStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventType;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.event.LogResolvedEvent;
import com.soyunju.logcollector.dto.event.LogSavedEvent;
import com.soyunju.logcollector.repository.kb.KbEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbEventOutboxProcessorService {

    private static final int MAX_ATTEMPTS = 5;
    // 재시도 간격: 1분 * 시도 횟수
    private static final long RETRY_INTERVAL_MINUTES = 1L;
    private static final int BATCH_SIZE = 20;

    private final KbEventOutboxRepository kbEventOutboxRepository;
    private final IncidentBridgeService incidentBridgeService;
    private final KbDraftService kbDraftService;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "kbTransactionManager")
    public void process(LocalDateTime now) {
        List<KbEventOutbox> targets = kbEventOutboxRepository
                .findRetryTargets(now, PageRequest.of(0, BATCH_SIZE));

        if (targets.isEmpty()) return;

        log.info("[KB][OUTBOX][RETRY] 재시도 대상 수={}", targets.size());

        for (KbEventOutbox outbox : targets) {
            processOne(outbox, now);
        }
    }

    private void processOne(KbEventOutbox outbox, LocalDateTime now) {
        int nextAttempt = outbox.getAttemptCount() + 1;

        // 최대 재시도 초과 시 FAILED 처리
        if (nextAttempt > MAX_ATTEMPTS) {
            kbEventOutboxRepository.updateState(
                    outbox.getId(),
                    KbEventOutboxStatus.FAILED,
                    outbox.getAttemptCount(),
                    null,
                    "최대 재시도 횟수 초과 (" + MAX_ATTEMPTS + "회)"
            );
            log.error("[KB][OUTBOX][FAIL] 최대 재시도 초과. outboxId={}, logHash={}", outbox.getId(), outbox.getLogHash());
            return;
        }

        try {
            replay(outbox);

            kbEventOutboxRepository.updateState(
                    outbox.getId(),
                    KbEventOutboxStatus.SUCCESS,
                    nextAttempt,
                    null,
                    null
            );
            log.info("[KB][OUTBOX][SUCCESS] 재처리 성공. outboxId={}, logHash={}", outbox.getId(), outbox.getLogHash());

        } catch (Exception e) {
            LocalDateTime nextRetry = now.plusMinutes(RETRY_INTERVAL_MINUTES * nextAttempt);
            kbEventOutboxRepository.updateState(
                    outbox.getId(),
                    KbEventOutboxStatus.PENDING,
                    nextAttempt,
                    nextRetry,
                    e.getMessage()
            );
            log.warn("[KB][OUTBOX][RETRY] 재처리 실패. outboxId={}, attempt={}, nextRetry={}, err={}",
                    outbox.getId(), nextAttempt, nextRetry, e.getMessage());
        }
    }

    private void replay(KbEventOutbox outbox) throws Exception {
        if (outbox.getEventType() == KbEventType.LOG_SAVED) {
            LogSavedEvent event = objectMapper.readValue(outbox.getPayload(), LogSavedEvent.class);
            incidentBridgeService.recordOccurrence(
                    event.getLogHash(),
                    event.getServiceName(),
                    event.getSummary(),
                    event.getStackTrace(),
                    event.getErrorCode(),
                    event.getEffectiveLevel(),
                    event.getOccurredTime()
            );

        } else if (outbox.getEventType() == KbEventType.LOG_RESOLVED) {
            LogResolvedEvent event = objectMapper.readValue(outbox.getPayload(), LogResolvedEvent.class);
            incidentBridgeService.markResolved(event.getLogHash(), event.getResolvedAt());
            incidentBridgeService.updateStatus(event.getLogHash(), IncidentStatus.RESOLVED);
        }
    }
}