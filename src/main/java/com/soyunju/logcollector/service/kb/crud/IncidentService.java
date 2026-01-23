package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
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



    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public Optional<Incident> findByLogHash(String logHash) {
        return incidentRepository.findByLogHash(logHash);
    }

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse> findAll(org.springframework.data.domain.Pageable pageable) {
        return incidentRepository.findAll(pageable).map(com.soyunju.logcollector.dto.kb.IncidentResponse::from);
    }

    @Transactional(transactionManager = "kbTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Incident recordOccurrence(String logHash, String serviceName, String summary, String stackTrace, String errorCode, String logLevel, LocalDateTime occurredAt) {
        LocalDateTime ts = (occurredAt != null) ? occurredAt : LocalDateTime.now();
        String level = mapErrorLevel(logLevel).name();
        incidentRepository.upsertIncident(logHash, serviceName, summary, stackTrace, errorCode, level, ts);
        return incidentRepository.findByLogHash(logHash).orElseThrow(() -> new IllegalStateException("Incident Upsert 실패: " + logHash));
    }

    // RESOLVED -> KB
    @Transactional(transactionManager = "kbTransactionManager")
    public void updateStatus(String logHash, com.soyunju.logcollector.domain.kb.enums.IncidentStatus status) {
        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        incident.setStatus(status);

        if (status == com.soyunju.logcollector.domain.kb.enums.IncidentStatus.RESOLVED) {
            incident.setResolvedAt(java.time.LocalDateTime.now());
            // RESOLVED 시점에만 KB 초안 생성 (이미 존재하면 내부 로직에서 방어)
            kbDraftService.createSystemDraft(incident.getId());
        } else {
            incident.setResolvedAt(null);
        }
        kbArticleRepository.findByIncident_Id(incident.getId()).ifPresent(kb -> {
            if (status == IncidentStatus.OPEN) {
                kb.setStatus(KbStatus.OPEN);
            } else if (status == IncidentStatus.UNDERWAY) {
                kb.setStatus(KbStatus.UNDERWAY);
            } else if (status == IncidentStatus.RESOLVED) {
                kb.setStatus(KbStatus.RESPONDED);
            }
        });
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

    public AiAnalysisResult analyze(String logHash) {
        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));
        // log_hash로 KB 찾기
        return kbArticleRepository.findTopByIncident_LogHashOrderByCreatedAtDesc(logHash)
                .map(kb -> new AiAnalysisResult(
                        "[기존 지식 참조] " + kb.getIncidentTitle(),
                        kb.getContent()
                ))
                // KB에 없으면 AI 분석 호출
                .orElseGet(() -> aiAnalysisService.AiAnalyze(incident.getId()));
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void updateDetails(String logHash, String title, String createdBy, IncidentStatus status) {
        incidentRepository.findByLogHash(logHash).ifPresent(incident -> {
            if (title != null && !title.isBlank()) {
                incident.setIncidentTitle(title);
            }
            if (createdBy != null && !createdBy.isBlank()) {
                incident.setCreatedBy(createdBy);
            }
            //  RESOLVED Draft
            if (status != null) {
                // Incident -> KB Status 동기화
                kbArticleRepository.findByIncident_Id(incident.getId()).ifPresent(kb -> {
                    if (status == IncidentStatus.OPEN) {
                        kb.setStatus(KbStatus.OPEN);
                    } else if (status == IncidentStatus.UNDERWAY) {
                        kb.setStatus(KbStatus.UNDERWAY);
                    } else if (status == IncidentStatus.RESOLVED) {
                        kb.setStatus(KbStatus.RESPONDED);
                    }
                });
                incident.setStatus(status);
                if (status == IncidentStatus.RESOLVED) {
                    if (incident.getResolvedAt() == null) {
                        incident.setResolvedAt(LocalDateTime.now());
                    }
                    kbDraftService.createSystemDraft(incident.getId());
                } else {
                    incident.setResolvedAt(null);
                }
            }
            incidentRepository.save(incident);

            kbArticleRepository.findByIncident_Id(incident.getId()).ifPresent(kb -> {
                if (title != null && !title.isBlank()) {
                    kb.setIncidentTitle(title);
                    kbArticleRepository.save(kb);
                }
            });
        });
    }

}