package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.LcIgnoreOutbox;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxAction;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxStatus;
import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.kb.LcIgnoreOutboxRepository;
import com.soyunju.logcollector.service.kb.ai.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "kbTransactionManager")
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AiAnalysisService aiAnalysisService;
    private final KbArticleRepository kbArticleRepository;
    private final KbDraftService kbDraftService;
    private final LcIgnoreOutboxRepository lcIgnoreOutboxRepository;


    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public Optional<Incident> findByLogHash(String logHash) {
        return incidentRepository.findByLogHash(logHash);
    }

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse> findAll(org.springframework.data.domain.Pageable pageable) {
        return incidentRepository
                .findByStatusNot(IncidentStatus.CLOSED, pageable)
                .map(com.soyunju.logcollector.dto.kb.IncidentResponse::from);
    }

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse> findClosed(org.springframework.data.domain.Pageable pageable) {
        return incidentRepository
                .findByStatus(IncidentStatus.CLOSED, pageable)
                .map(com.soyunju.logcollector.dto.kb.IncidentResponse::from);
    }

    @Transactional(transactionManager = "kbTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Incident recordOccurrence(String logHash, String serviceName, String summary, String stackTrace, String errorCode, String logLevel, LocalDateTime occurredAt) {
        LocalDateTime ts = (occurredAt != null) ? occurredAt : LocalDateTime.now();
        String level = mapErrorLevel(logLevel).name();

        // 재발생 감지용
        IncidentStatus prevStatus = incidentRepository.findByLogHash(logHash)
                .map(Incident::getStatus)
                .orElse(null);

        incidentRepository.upsertIncident(logHash, serviceName, summary, stackTrace, errorCode, level, ts);

        Incident saved = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Incident Upsert 실패: " + logHash));

        // 재발생시 status 변경(OPEN)
        if (prevStatus == IncidentStatus.RESOLVED || prevStatus == IncidentStatus.CLOSED) {
            kbArticleRepository.markRecurByIncidentId(saved.getId(), LocalDateTime.now());
        }
        return saved;
    }

    // IGNORED 처리 로직 추가
    @Transactional(transactionManager = "kbTransactionManager")
    public void updateStatus(String logHash, IncidentStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 null일 수 없습니다.");
        }

        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        IncidentStatus prev = incident.getStatus();

        // 동일 상태면 아무것도 하지 않음
        if (prev == status) {
            return;
        }

        incident.setStatus(status);

        //  RESOLVED => resolvedAt + Draft 생성
        if (status == IncidentStatus.RESOLVED) {
            if (incident.getResolvedAt() == null) {
                incident.setResolvedAt(LocalDateTime.now());
            }
            kbDraftService.createSystemDraft(incident.getId());
        }

        // IGNORED
        if (prev != IncidentStatus.IGNORED && status == IncidentStatus.IGNORED) {
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
        } else if (prev == IncidentStatus.IGNORED && status != IncidentStatus.IGNORED) {
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

        incidentRepository.save(incident);
    }


    @Transactional(transactionManager = "kbTransactionManager")
    public void markResolved(String logHash, LocalDateTime resolvedAt) {
        updateStatus(logHash, IncidentStatus.RESOLVED);
    }

    private ErrorLevel mapErrorLevel(String logLevel) {
        if (logLevel == null) return ErrorLevel.ERROR;
        String v = logLevel.trim().toUpperCase();
        try {
            return ErrorLevel.valueOf(v);
        } catch (Exception e) {
            return ErrorLevel.ERROR;
        }
    }

    public AiAnalysisResult analyze(String logHash, boolean force) {
        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        // 1. Force 모드가 아니고(false), KB가 있다면 -> KB 내용 반환
        if (!force) {
            var kbOpt = kbArticleRepository.findTopByIncident_LogHashOrderByCreatedAtDesc(logHash);
            if (kbOpt.isPresent()) {
                var kb = kbOpt.get();
                return new AiAnalysisResult(
                        "[기존 지식 참조] " + kb.getIncidentTitle(),
                        kb.getContent()
                );
            }
        }
        // 2. Force 모드이거나(true), KB가 없으면 -> AI 분석 호출
        return aiAnalysisService.AiAnalyze(incident.getId());
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void updateDetails(String logHash, String title, String createdBy, IncidentStatus status) {

        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        boolean changed = false;

        if (title != null && !title.isBlank() && !title.equals(incident.getIncidentTitle())) {
            incident.setIncidentTitle(title);
            changed = true;
        }

        if (createdBy != null && !createdBy.isBlank() && !createdBy.equals(incident.getCreatedBy())) {
            incident.setCreatedBy(createdBy);
            changed = true;
            if (incident.getStatus() == IncidentStatus.OPEN) {
                incident.setStatus(IncidentStatus.UNDERWAY);
            }
        }
        if (status != null) {
            IncidentStatus prev = incident.getStatus();

            if (prev != status) {
                incident.setStatus(status);
                changed = true;

                if (status == IncidentStatus.RESOLVED) {
                    if (incident.getResolvedAt() == null) {
                        incident.setResolvedAt(LocalDateTime.now());
                        changed = true;
                    }
                    kbDraftService.createSystemDraft(incident.getId());
                }

                // IGNORED outbox
                if (prev != IncidentStatus.IGNORED && status == IncidentStatus.IGNORED) {
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
                } else if (prev == IncidentStatus.IGNORED && status != IncidentStatus.IGNORED) {
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
        }

        if (changed) {
            incidentRepository.save(incident);
        }

        // KBArticle title 동기화
        if (title != null && !title.isBlank()) {
            kbArticleRepository.findByIncident_Id(incident.getId()).ifPresent(kb -> {
                if (!title.equals(kb.getIncidentTitle())) {
                    kb.setIncidentTitle(title);
                    kbArticleRepository.save(kb);
                }
            });
        }
    }


}