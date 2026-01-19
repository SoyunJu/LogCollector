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

import java.time.LocalDateTime;

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
                .orElseThrow(() -> new IllegalArgumentException("Incident를 찾을 수 없습니다."));

        String body = String.format("""
                ## 에러 코드: %s
                ## 요약: %s
                ## 원본 :
                %s
                """, incident.getErrorCode(), incident.getSummary(), incident.getStackTrace());

        String serviceName = (incident.getServiceName() != null && !incident.getServiceName().isBlank())
                ? incident.getServiceName()
                : "unknown-service";

        // title 식별자: 발생일(YYYY-MM-DD) 우선 사용
        String dateOnly = formatDateOnly(
                incident.getFirstOccurredAt(),
                incident.getLastOccurredAt()
        );

        String title = String.format("[SYSTEM] 에러 현상을 입력하세요 [%s / %s]", serviceName, dateOnly);

        if (title.length() > 255) title = title.substring(0, 255);

        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(title)
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
