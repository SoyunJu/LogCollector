package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.SystemDraft;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.kb.SystemDraftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class KbDraftService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;
    private final SystemDraftRepository systemDraftRepository;
    private static final List<KbStatus> ACTIVE_DRAFT_STATUSES =
            List.of(KbStatus.DRAFT, KbStatus.IN_PROGRESS);

    public KbDraftService(IncidentRepository incidentRepository,
                          KbArticleRepository kbArticleRepository,
                          SystemDraftRepository systemDraftRepository) {
        this.incidentRepository = incidentRepository;
        this.kbArticleRepository = kbArticleRepository;
        this.systemDraftRepository = systemDraftRepository;
    }

    // LC 에서 RESOLVED 된 ERROR LOG 처리
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraft(Long incidentId) {
        return createSystemDraft(incidentId, 0, 0, DraftReason.HIGH_RECUR);
    }

    // System Draft
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraft(Long incidentId,
                                  int hostCount,
                                  int repeatCount,
                                  DraftReason reason) {

        // 중복 방지
        Optional<KbArticle> existingKb = kbArticleRepository.findByIncident_Id(incidentId);
        if (existingKb.isPresent()) {
            return existingKb.get().getId();
        }
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident를 찾을 수 없습니다. incidentId=" + incidentId));

        SystemDraft savedDraft = null;
        try {
            savedDraft = systemDraftRepository.save(
                    SystemDraft.builder()
                            .incident(incident)
                            .hostCount(hostCount)
                            .repeatCount(repeatCount)
                            .reason(reason)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("[SYSTEM][SKIP] system_draft가 이미 존재합니다. incidentId={}", incidentId);
        }

        // KBArticle draft 생성
        Long kbArticleId = createSystemKbArticle(incident);

        if (savedDraft != null) {
            savedDraft.setCreatedKbArticleId(kbArticleId);
            systemDraftRepository.save(savedDraft);
        }

        return kbArticleId;
    }

    // draft 유무 체크
    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public Optional<Long> findActiveSystemDraftId(Long incidentId) {
        return kbArticleRepository
                .findTopByIncident_IdAndCreatedByAndStatusInOrderByCreatedAtDesc(
                        incidentId, CreatedBy.system, ACTIVE_DRAFT_STATUSES
                )
                .map(KbArticle::getId);
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void updateDraft(Long kbArticleId, String title, String content, String createdBy) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 Draft를 찾을 수 없습니다. ID: " + kbArticleId));

        if (kb.getStatus() == KbStatus.PUBLISHED || kb.getStatus() == KbStatus.ARCHIVED) {
            throw new IllegalStateException("PUBLISHED/ARCHIVED 상태에서는 수정할 수 없습니다. kbArticleId=" + kbArticleId);
        }
        // title 동기화
        if (title != null && !title.isBlank()) {
            kb.setIncidentTitle(title);
            if (kb.getIncident() != null) {
                kb.getIncident().setIncidentTitle(title);
            }
        }
        if (content != null) {
            kb.setContent(content);
        }
        // createdBy
        if (createdBy != null && !createdBy.isBlank()) {
            try {
                CreatedBy cb = CreatedBy.valueOf(createdBy.toLowerCase());
                kb.setCreatedBy(cb);
            } catch (IllegalArgumentException ignore) {
            }
        }

        boolean hasTitle = kb.getIncidentTitle() != null && !kb.getIncidentTitle().isBlank();
        boolean hasContent = kb.getContent() != null && !kb.getContent().isBlank();

        if (hasTitle && hasContent) {
            kb.setStatus(KbStatus.PUBLISHED);
            if (kb.getPublishedAt() == null) {
                kb.setPublishedAt(LocalDateTime.now());
            }
            if (kb.getIncident() != null && kb.getIncident().getCloseEligibleAt() == null) {
                kb.getIncident().setCloseEligibleAt(LocalDateTime.now());
            }
        } else {
            kb.setStatus(KbStatus.IN_PROGRESS);
        }
        kb.touchActivity();
        kbArticleRepository.save(kb);
    }

    // System create KbArticle
    @Transactional(transactionManager = "kbTransactionManager")
    private Long createSystemKbArticle(Incident incident) {

        Optional<KbArticle> existing = kbArticleRepository.findByIncident_Id(incident.getId());
        if (existing.isPresent()) return existing.get().getId();

        String serviceName = (incident.getServiceName() == null || incident.getServiceName().isBlank())
                ? "unknown-service"
                : incident.getServiceName();

        String errorCode = incident.getErrorCode();
        // 40자로 자르기. 필요시 50 등으로 늘리기 가능
        String summaryShort = normalizeAndTruncate(incident.getSummary(), 40);

        String title;
        if (errorCode != null && !errorCode.isBlank()) {
            title = "[SYSTEM][" + serviceName + "] " + errorCode;
            if (summaryShort != null && !summaryShort.isBlank()) {
                title += " - " + summaryShort;
            }
        } else {
            String hashPrefix = incident.getLogHash() != null
                    ? incident.getLogHash().substring(0, Math.min(8, incident.getLogHash().length()))
                    : "unknown";
            title = "[SYSTEM][" + serviceName + "] Incident #" + hashPrefix;
        }

        // DB 길이 보호
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }
        // title 동기화
        incident.setIncidentTitle(title);

        String body = String.format("""
                        ## 에러 코드: %s
                        ## 요약: %s
                        ## 발생/최근 발생: %s / %s
                        ## 반복 횟수: %s
                        ## 스택트레이스:
                        %s
                        """,
                nullToDash(incident.getErrorCode()),
                nullToDash(incident.getSummary()),
                incident.getFirstOccurredAt(),
                incident.getLastOccurredAt(),
                incident.getRepeatCount(),
                nullToDash(incident.getStackTrace())
        );

        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(title)
                .content(body)
                .status(KbStatus.DRAFT)
                .createdBy(CreatedBy.system)
                .updatedAt(LocalDateTime.now())
                .build();

        return kbArticleRepository.save(kb).getId();
    }

    private static String nullToDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    // 7일간 초안 유지 -> DEL 조치
    @Transactional(transactionManager = "kbTransactionManager")
    public void cleanupExpiredDrafts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        kbArticleRepository.deleteExpiredSystemDrafts(
                CreatedBy.system,
                KbStatus.DRAFT,
                threshold, // created_at
                threshold  // last_activity_at
        );
    }

    // response 채우기
    public static KbArticleResponse from(KbArticle kb) {
        Incident inc = kb.getIncident();

        return KbArticleResponse.builder()
                .id(kb.getId())
                .incidentId(inc != null ? inc.getId() : null)
                .incidentTitle(kb.getIncidentTitle())
                .content(kb.getContent())
                .status(kb.getStatus() != null ? kb.getStatus().name() : null)
                .confidenceLevel(kb.getConfidenceLevel())
                .createdBy(kb.getCreatedBy() != null ? kb.getCreatedBy().name() : null)
                .lastActivityAt(kb.getLastActivityAt())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .serviceName(inc != null ? inc.getServiceName() : null)
                .errorCode(inc != null ? inc.getErrorCode() : null)
                .title(null)
                .build();
    }

    // summary 자르기 헬퍼
    private static String normalizeAndTruncate(String s, int maxLen) {
        if (s == null) return null;

        String cleaned = s
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen - 1) + "…";
    }

}
