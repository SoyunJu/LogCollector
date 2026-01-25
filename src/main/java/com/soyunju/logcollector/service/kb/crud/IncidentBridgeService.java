package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.LcIgnoreOutbox;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxAction;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.LcIgnoreOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "kbTransactionManager")
public class IncidentBridgeService {

    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService; // KB-only 서비스
    private final LcIgnoreOutboxRepository lcIgnoreOutboxRepository; // LC 동기화(outbox)는 여기서만

    @Transactional(transactionManager = "kbTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Incident recordOccurrence(
            String logHash,
            String serviceName,
            String summary,
            String stackTrace,
            String errorCode,
            String logLevel,
            LocalDateTime occurredAt
    ) {
        return incidentService.recordOccurrenceKbOnly(
                logHash, serviceName, summary, stackTrace, errorCode, logLevel, occurredAt
        );
    }


    public void markResolved(String logHash, LocalDateTime resolvedAt) {
        // RESOLVED 자체는 KB-only 처리. (outbox 필요 없음)
        incidentService.markResolvedKbOnly(logHash, resolvedAt);
    }

    public void updateStatus(String logHash, IncidentStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus는 null일 수 없습니다.");
        }

        var prev = incidentService.findByLogHash(logHash)
                .map(i -> i.getStatus())
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        // KB DB 업데이트
        incidentService.updateStatusKbOnly(logHash, newStatus);

        // LC 동기화가 필요한 전이만 outbox 적재 (IGNORED <-> others)
        enqueueIgnoreOutboxIfNeeded(logHash, prev, newStatus);
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void unignore(String logHash) {

        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. logHash=" + logHash));

        if (incident.getStatus() != IncidentStatus.IGNORED) {
            throw new IllegalStateException("IGNORED 인시던트만 변경 가능합니다. 현재=" + incident.getStatus());
        }

        incident.setStatus(IncidentStatus.OPEN);

        lcIgnoreOutboxRepository.save(
                LcIgnoreOutbox.requestUnignore(logHash)
        );
    }


    public void updateDetails(String logHash, String title, String createdBy, IncidentStatus status) {
        IncidentStatus prev = null;
        if (status != null) {
            prev = incidentService.findByLogHash(logHash)
                    .map(i -> i.getStatus())
                    .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));
        }

        // KB-only 업데이트
        incidentService.updateDetailsKbOnly(logHash, title, createdBy, status);

        // status 변경이 들어온 경우에만 outbox 처리
        if (status != null && prev != null) {
            enqueueIgnoreOutboxIfNeeded(logHash, prev, status);
        }
    }

    private void enqueueIgnoreOutboxIfNeeded(String logHash, IncidentStatus prev, IncidentStatus next) {
        if (prev == next) return;

        if (prev != IncidentStatus.IGNORED && next == IncidentStatus.IGNORED) {
            lcIgnoreOutboxRepository.save(
                    LcIgnoreOutbox.builder()
                            .logHash(logHash)
                            .action(LcIgnoreOutboxAction.IGNORE)
                            .status(LcIgnoreOutboxStatus.PENDING)
                            .attemptCount(0)
                            .nextRetryAt(null)
                            .lastError(null)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build()
            );
            return;
        }

        if (prev == IncidentStatus.IGNORED && next != IncidentStatus.IGNORED) {
            lcIgnoreOutboxRepository.save(
                    LcIgnoreOutbox.builder()
                            .logHash(logHash)
                            .action(LcIgnoreOutboxAction.UNIGNORE)
                            .status(LcIgnoreOutboxStatus.PENDING)
                            .attemptCount(0)
                            .nextRetryAt(null)
                            .lastError(null)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build()
            );
        }
    }

    /*
    public void unignore(String logHash) {
        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow();

        incident.setStatus(IncidentStatus.OPEN);

        lcIgnoreOutboxRepository.save(
                LcIgnoreOutbox.unignore(logHash)
        );
    } */



}
