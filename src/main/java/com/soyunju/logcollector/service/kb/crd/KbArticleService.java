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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbArticleService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;
    private final SystemDraftRepository systemDraftRepository;

    // Draft (초안) 여부 확인을 위한 상태 리스트
    private static final List<KbStatus> ACTIVE_DRAFT_STATUSES =
            List.of(KbStatus.OPEN, KbStatus.UNDERWAY);

    @Transactional(transactionManager = "kbTransactionManager")
    private Long createSystemKbArticle(Long incidentId) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident를 찾을 수 없습니다. incidentId=" + incidentId));

        String serviceName = (incident.getServiceName() == null || incident.getServiceName().isBlank())
                ? "unknown-service"
                : incident.getServiceName();

        String title = "[SYSTEM] 에러 현상을 입력하세요 [" + serviceName + "]";
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
                .publishedAt(CreatedBy.system)
                .build();

        return kbArticleRepository.save(kb).getId();
    }

    // KB 전체 목록 조회 (index.html 필드명에 맞춰 title 매핑)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.KbArticleResponse> findAll(org.springframework.data.domain.Pageable pageable) {
        return kbArticleRepository.findAll(pageable).map(kb -> com.soyunju.logcollector.dto.kb.KbArticleResponse.builder()
                .id(kb.getId())
                .title(kb.getIncidentTitle() != null ? kb.getIncidentTitle() : "시스템 생성 초안 (내용 확인 필요)")
                .status(kb.getStatus().name())
                .createdAt(kb.getCreatedAt())
                .build());
    }

    // LC 에서 RESOLVED 된 ERROR LOG 처리
    // Draft (초안) 단계
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraft(Long incidentId) {
        return createSystemDraftIfAbsent(incidentId, 0, 0, DraftReason.HIGH_RECUR);
    }

    // System Draft 중복 방지
    @Transactional(transactionManager = "kbTransactionManager")
    public Long createSystemDraftIfAbsent(Long incidentId,
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

    @Transactional(readOnly = true)
    public Optional<Long> findActiveSystemDraftId(Long incidentId) {
        // 수정: 파라미터 개수(CreatedBy.system 추가) 맞춤
        return kbArticleRepository
                .findTopByIncident_IdAndCreatedByAndStatusInOrderByCreatedAtDesc(
                        incidentId, CreatedBy.system, ACTIVE_DRAFT_STATUSES
                )
                .map(KbArticle::getId);
    }


    // 2. 사용자 입력 단계 (title-현상 필수, content-해결안은 nullable)
    @Transactional
    public void postArticle(Long kbArticleId, String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("장애 현상을 입력해주세요.");
        }
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("히스토리를 찾을 수 없습니다."));

        kb.setIncidentTitle(title);
        kb.setContent(content != null ? content : kb.getContent());

        // 상태 결정 로직
        if (content == null || content.isBlank()) {
            kb.setStatus(KbStatus.UNDERWAY); // error 해결 진행 중
        } else {
            kb.setStatus(KbStatus.RESPONDED); // 해결 방안 입력시 장애 해결 상태로 간주
        }
        kb.touchActivity();
    }

    private String formatDateOnly(LocalDateTime firstOccurredAt, LocalDateTime lastOccurredAt) {
        LocalDateTime base = (firstOccurredAt != null) ? firstOccurredAt
                : (lastOccurredAt != null) ? lastOccurredAt
                : LocalDateTime.now();
        return base.toLocalDate().toString(); // yyyy-MM-dd
    }

    private String shortHash(String logHash) {
        if (logHash == null || logHash.isBlank()) return "nohash";
        return logHash.length() <= 8 ? logHash : logHash.substring(0, 8);
    }

}
