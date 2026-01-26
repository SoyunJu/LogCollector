package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.KbAddendum;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.service.kb.ai.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    private final KbAddendumRepository kbAddendumRepository;

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
    public Incident recordOccurrenceKbOnly(
            String logHash,
            String serviceName,
            String summary,
            String stackTrace,
            String errorCode,
            String logLevel,
            LocalDateTime occurredAt
    ) {
        LocalDateTime ts = (occurredAt != null) ? occurredAt : LocalDateTime.now();
        String level = mapErrorLevel(logLevel).name();

        Incident existing = incidentRepository.findByLogHash(logHash).orElse(null);
        IncidentStatus prevStatus = (existing != null) ? existing.getStatus() : null;

        // IGNORED 처리: last_occurred_at만 갱신
        if (prevStatus == IncidentStatus.IGNORED) {
            incidentRepository.touchLastOccurredIfIgnored(logHash, ts);
            return incidentRepository.findByLogHash(logHash)
                    .orElseThrow(() -> new IllegalStateException("Incident not found after IGNORE touch: " + logHash));
        }

        //  타이틀 생성
        String incidentTitle = generateIncidentTitle(serviceName, errorCode, logHash, summary);

        incidentRepository.upsertIncident(
                logHash, serviceName, incidentTitle, summary, stackTrace, errorCode, level, ts
        );

        Incident saved = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Incident Upsert 실패: " + logHash));

        // 재발생시 status 변경(OPEN) 및 KB recurrence 기록
        if (prevStatus == IncidentStatus.RESOLVED || prevStatus == IncidentStatus.CLOSED) {
            kbArticleRepository.markRecurByIncidentId(saved.getId(), LocalDateTime.now());
        }
        return saved;
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void updateStatusKbOnly(String logHash, IncidentStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 null일 수 없습니다.");
        }

        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        IncidentStatus prev = incident.getStatus();
        if (prev == status) {
            return;
        }

        incident.setStatus(status);

        // RESOLVED => resolvedAt + closeEligibleAt + Draft 생성 (KB DB only)
        if (status == IncidentStatus.RESOLVED) {
            if (incident.getResolvedAt() == null) {
                incident.setResolvedAt(LocalDateTime.now());
            }
            if (incident.getCloseEligibleAt() == null) {
                incident.setCloseEligibleAt(incident.getResolvedAt().plusHours(2));
            }
            kbDraftService.createSystemDraft(incident.getId());
        }

        incidentRepository.save(incident);
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void markResolvedKbOnly(String logHash, LocalDateTime resolvedAt) {
        // resolvedAt은 현재 구현에서는 now 기반 처리(필요 시 확장)
        updateStatusKbOnly(logHash, IncidentStatus.RESOLVED);
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

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public AiAnalysisResult analyze(String logHash, boolean force) {
        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. hash: " + logHash));

        // KB 존재 여부 확인
        Optional<KbArticle> kbOpt = kbArticleRepository.findTopByIncident_LogHashOrderByCreatedAtDesc(logHash);
        Long kbId = kbOpt.map(KbArticle::getId).orElse(null);

        // 1. Force 모드가 아니고(false), KB가 있다면 -> Addendum 확인
        if (!force && kbOpt.isPresent()) {
            KbArticle kb = kbOpt.get();

            // 최신 Addendum 3개 조회
            List<KbAddendum> recentAddendums = kbAddendumRepository.findTop3ByKbArticle_IdOrderByCreatedAtDesc(kb.getId());

            if (!recentAddendums.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("=== [기존 지식 참조] ===\n");

                for (KbAddendum addendum : recentAddendums) {
                    sb.append(String.format("\n[Date: %s | By: %s]\n%s\n",
                            addendum.getCreatedAt(),
                            addendum.getCreatedBy(),
                            addendum.getContent()));
                }

                return new AiAnalysisResult(
                        "[AI분석 결과] " + kb.getIncidentTitle(),
                        sb.toString(),
                        kbId
                );
            }
        }
        AiAnalysisResult aiResult = aiAnalysisService.AiAnalyze(incident.getId());
        return new AiAnalysisResult(aiResult.getCause(), aiResult.getSuggestion(), kbId);
    }

    public void updateDetailsKbOnly(String logHash, String title, String createdBy, IncidentStatus status) {

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
                incident.setStatus(IncidentStatus.IN_PROGRESS);
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
                    if (incident.getCloseEligibleAt() == null) {
                        incident.setCloseEligibleAt(incident.getResolvedAt().plusHours(2));
                        changed = true;
                    }
                    kbDraftService.createSystemDraft(incident.getId());
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

    @Transactional(transactionManager = "kbTransactionManager")
    public int autoCloseIncidents(LocalDateTime now) {

        var candidates = incidentRepository.findCloseCandidates(IncidentStatus.RESOLVED, now);

        log.info("[AUTO_CLOSE] now={} candidates={}", now, (candidates == null ? 0 : candidates.size()));

        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        for (Incident i : candidates) {
            i.setStatus(IncidentStatus.CLOSED);
            i.setClosedAt(now);
            i.setCloseEligibleAt(null);
        }

        incidentRepository.saveAll(candidates);
        return candidates.size();
    }

    private String generateIncidentTitle(String serviceName, String errorCode, String logHash, String summary) {
        String safeService = (serviceName == null || serviceName.isBlank()) ? "unknown-service" : serviceName;

        String summaryShort = normalizeAndTruncate(summary, 50);

        String title;

        if (errorCode != null && !errorCode.isBlank()) {
            title = "[" + safeService + "] [" + errorCode + "]";
            if (summaryShort != null && !summaryShort.isBlank()) {
                title += summaryShort;
            }
        } else {
            String hashPrefix = (logHash != null)
                    ? logHash.substring(0, Math.min(8, logHash.length()))
                    : "unknown";
            title = "[" + safeService + "] Incident #" + hashPrefix;
        }

        if (title.length() > 255) {
            title = title.substring(0, 255);
        }
        return title;
    }

    private String normalizeAndTruncate(String str, int length) {
        if (str == null) return null;
        // 공백/개행 정리
        String normalized = str.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return null;
        return (normalized.length() > length) ? normalized.substring(0, length) : normalized;
    }



}
