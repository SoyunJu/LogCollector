package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.SystemDraft;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
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

    // Draft (초안) 여부 확인을 위한 상태 리스트
    private static final List<KbStatus> ACTIVE_DRAFT_STATUSES =
            List.of(KbStatus.OPEN, KbStatus.UNDERWAY);

    public KbDraftService(IncidentRepository incidentRepository, KbArticleRepository kbArticleRepository, SystemDraftRepository systemDraftRepository, KbCrdService kbCrdService) {
        this.incidentRepository = incidentRepository;
        this.kbArticleRepository = kbArticleRepository;
        this.systemDraftRepository = systemDraftRepository;
    }

    // LC 에서 RESOLVED 된 ERROR LOG 처리
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraft(Long incidentId) {
        return createSystemDraft(incidentId, 0, 0, DraftReason.HIGH_RECUR);
    }

    // System Draft 중복 방지 적용
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraft(Long incidentId,
                                          int hostCount,
                                          int repeatCount,
                                          DraftReason reason) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident를 찾을 수 없습니다. incidentId=" + incidentId));

        try {
            SystemDraft draft = systemDraftRepository.save(
                    SystemDraft.builder()
                            .incident(incident)
                            .hostCount(hostCount)
                            .repeatCount(repeatCount)
                            .reason(reason)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
            Long kbArticleId = createSystemKbArticle(incidentId);
            // system_draft 테이블과 kb_article 테이블 연결
            draft.setCreatedKbArticleId(kbArticleId);
            systemDraftRepository.save(draft);

            return kbArticleId;

        } catch (DataIntegrityViolationException e) {
            log.info("[SYSTEM][SKIP] DRAFT가 이미 존재합니다. incidentId={}", incidentId);
            return null;
        }
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
    public void updateDraft(Long kbArticleId, String title, String content) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 Draft를 찾을 수 없습니다. ID: " + kbArticleId));

        if (title != null && !title.isBlank()) {
            kb.setIncidentTitle(title);
            // incident 동기화
            if (kb.getIncident() != null) {
                kb.getIncident().setIncidentTitle(title);
            }
        }
        kb.setContent(content != null ? content : kb.getContent());

        // status 변경 조건 로직
        if (org.springframework.util.StringUtils.hasText(content)) {
            kb.setStatus(KbStatus.RESPONDED);
        } else {
            kb.setStatus(KbStatus.UNDERWAY);
        }
        kb.touchActivity();
    }

    // System create KbArticle
    @Transactional(transactionManager = "kbTransactionManager")
    private Long createSystemKbArticle(Long incidentId) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident를 찾을 수 없습니다. incidentId=" + incidentId));

        String serviceName = (incident.getServiceName() == null || incident.getServiceName().isBlank())
                ? "unknown-service"
                : incident.getServiceName();

        String title = "[SYSTEM] 에러 현상을 입력하세요 [" + serviceName + "]";
        incident.setIncidentTitle(title);

        if (title.length() > 255) title = title.substring(0, 255);

        String body = String.format("""
                        ## 에러 코드: %s
                        ## 요약: %s
                        ## 스택트레이스:
                        %s
                        """,
                incident.getErrorCode(),
                incident.getSummary(),
                incident.getStackTrace()
        );

        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(title)
                .content(body)
                .status(KbStatus.OPEN)
                .createdBy(CreatedBy.system)
                .publishedAt(LocalDateTime.now())
                .build();

        return kbArticleRepository.save(kb).getId();
    }

}
