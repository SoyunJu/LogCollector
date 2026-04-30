package com.soyunju.logcollector.service.kb.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soyunju.logcollector.domain.kb.KbEventOutbox;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventOutboxStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventType;
import com.soyunju.logcollector.dto.event.LogResolvedEvent;
import com.soyunju.logcollector.dto.event.LogSavedEvent;
import com.soyunju.logcollector.dto.logfixer.LogFixerIncidentPayload;
import com.soyunju.logcollector.repository.kb.KbEventOutboxRepository;
import com.soyunju.logcollector.service.logfixer.LogFixerWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbEventListener {

    private final IncidentBridgeService incidentBridgeService;
    private final KbDraftService kbDraftService;
    private final KbEventOutboxRepository kbEventOutboxRepository;
    private final ObjectMapper objectMapper;
    private final LogFixerWebhookService logFixerWebhookService;

    // LC TX 커밋 완료 후 실행 -> LC 롤백시 실행X
    // KB가 LC에 영향 X
    @Async("kbEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "kbTransactionManager")
    public void onLogSaved(LogSavedEvent event) {
        // 6. Incident upsert (KB)
        try {
            incidentBridgeService.recordOccurrence(
                    event.getLogHash(),
                    event.getServiceName(),
                    event.getSummary(),
                    event.getStackTrace(),
                    event.getErrorCode(),
                    event.getEffectiveLevel(),
                    event.getOccurredTime()
            );
        } catch (Exception e) {
            log.warn("[KB][INCIDENT][SKIP] recordOccurrence failed. logHash={}, err={}",
                    event.getLogHash(), e.toString());
            // 실패 시 outbox 적재 → 스케줄러가 재시도
            saveToOutbox(event.getLogHash(), KbEventType.LOG_SAVED, event, e);
            return; // Incident 실패 시 Draft도 의미 없으므로 중단
        }

        // 7. Draft 생성 (조건 충족 시)
        if (event.isDraftNeeded() && event.getIncidentId() != null) {
            try {
                DraftReason reason = "HOST_SPREAD".equals(event.getDraftReason())
                        ? DraftReason.HOST_SPREAD
                        : DraftReason.HIGH_RECUR;
                kbDraftService.createSystemDraft(
                        event.getIncidentId(),
                        event.getImpactedHostCount(),
                        event.getRepeatCount(),
                        reason
                );
            } catch (Exception e) {
                log.warn("[KB][DRAFT][SKIP] createSystemDraft failed. incidentId={}, logHash={}, err={}",
                        event.getIncidentId(), event.getLogHash(), e.toString());
            }
        }
        // 8. LogFixer webhook
        try {
            logFixerWebhookService.sendIncident(
                    LogFixerIncidentPayload.builder()
                            .logHash(event.getLogHash())
                            .serviceName(event.getServiceName())
                            .summary(event.getSummary())
                            .stackTrace(event.getStackTrace())
                            .errorCode(event.getErrorCode())
                            .logLevel(event.getEffectiveLevel())
                            .occurredTime(event.getOccurredTime())
                            .impactedHostCount(event.getImpactedHostCount())
                            .repeatCount(event.getRepeatCount())
                            .build()
            );
        } catch (Exception e) {
            log.warn("[LogFixer][WEBHOOK][SKIP] 발송 중 예외. logHash={}, err={}", event.getLogHash(), e.getMessage());
        }
    }

    @Async("kbEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "kbTransactionManager")
    public void onLogResolved(LogResolvedEvent event) {
        // markResolved
        try {
            incidentBridgeService.markResolved(event.getLogHash(), event.getResolvedAt());
        } catch (Exception e) {
            log.warn("[KB][RESOLVED][SKIP] markResolved failed. logHash={}, err={}",
                    event.getLogHash(), e.toString());
            saveToOutbox(event.getLogHash(), KbEventType.LOG_RESOLVED, event, e);
            return;
        }

        // RESOLVED 상태 전이
        try {
            incidentBridgeService.updateStatus(event.getLogHash(), IncidentStatus.RESOLVED);
        } catch (Exception e) {
            log.warn("[KB][RESOLVED][SKIP] updateStatus failed. logHash={}, err={}",
                    event.getLogHash(), e.toString());
        }

        // Draft 생성
        if (event.getIncidentId() != null) {
            try {
                kbDraftService.createSystemDraft(event.getIncidentId());
            } catch (Exception e) {
                log.warn("[KB][DRAFT][SKIP] createSystemDraft on RESOLVED failed. incidentId={}, logHash={}, err={}",
                        event.getIncidentId(), event.getLogHash(), e.toString());
            }
        }
    }

    // 이벤트 처리 실패 시 KB DB에 outbox 적재 (스케줄러 재시도용)
    private void saveToOutbox(String logHash, KbEventType eventType, Object event, Exception cause) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kbEventOutboxRepository.save(KbEventOutbox.builder()
                    .logHash(logHash)
                    .eventType(eventType)
                    .payload(payload)
                    .status(KbEventOutboxStatus.PENDING)
                    .attemptCount(0)
                    .lastError(cause.getMessage())
                    .build());
            log.info("[KB][OUTBOX] 이벤트 저장 완료. logHash={}, type={}", logHash, eventType);
        } catch (Exception ex) {
            log.error("[KB][OUTBOX] 저장 실패 (이벤트 유실 가능). logHash={}, err={}", logHash, ex.getMessage());
        }
    }
}