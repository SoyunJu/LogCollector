package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KbArticleService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;

    // LC 에서 RESOLVED 된 ERROR LOG 처리
    // 1. Draft (초안) 단계
    @Transactional
    public Long createSystemDraft(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다."));

        String body = String.format("""
                ## 에러 코드: %s
                ## 요약: %s
                ## 원본 스택트레이스:
                %s
                """, incident.getErrorCode(), incident.getSummary(), incident.getStackTrace());

        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(null) // 의도적으로 null 설정, 현상은 사람이 입력
                .content(body)
                .status(KbStatus.OPEN)
                .createdBy(CreatedBy.system)
                .build();

        return kbArticleRepository.save(kb).getId();
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
}
