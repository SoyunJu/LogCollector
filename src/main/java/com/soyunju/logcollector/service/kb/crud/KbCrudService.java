package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "kbTransactionManager")
public class KbCrudService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;

    // 사용자 입력 단계 (title-현상 필수, content-해결안은 nullable)
    @Transactional
    public void postArticle(Long kbArticleId, String title, String content, String createdBy) {
        // KB Article 조회
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 KB를 찾을 수 없습니다."));

        // Incident 동기화
        if (kb.getIncident() != null) {
            Long incidentId = kb.getIncident().getId();
            Incident incident = incidentRepository.findById(incidentId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 Incident를 찾을 수 없습니다."));

            // Title 동기화
            if (StringUtils.hasText(title)) {
                incident.setIncidentTitle(title);
            }

            // CreatedBy 동기화
            if (StringUtils.hasText(createdBy)) {
                try {
                    incident.setCreatedBy(String.valueOf(CreatedBy.valueOf(createdBy)));
                } catch (IllegalArgumentException e) {
                    // Enum 매핑 실패 시 무시 or 예외 처리
                }
            }
        }

        if (StringUtils.hasText(title)) {
            kb.setIncidentTitle(title);
        }
        if (content != null) {
            kb.setContent(content);
        }

        if (StringUtils.hasText(createdBy)) {
            try {
                kb.setCreatedBy(CreatedBy.valueOf(createdBy));
            } catch (IllegalArgumentException e) {

            }

            if (kb.getCreatedBy() != CreatedBy.system) {
                if (StringUtils.hasText(title) && StringUtils.hasText(content)) {
                    kb.setStatus(KbStatus.RESPONDED);
                    kb.setUpdatedAt(LocalDateTime.now());
                    kb.setLastActivityAt(LocalDateTime.now());
                } else {
                    kb.setStatus(KbStatus.UNDERWAY);
                }
            }
            //  Incident 상태 역방향 동기화
            if (kb.getIncident() != null) {
                Incident incident = kb.getIncident();
                if (kb.getStatus() == KbStatus.RESPONDED) {
                    incident.setStatus(IncidentStatus.RESOLVED);
                }
                else if (kb.getStatus() == KbStatus.UNDERWAY) {
                    incident.setStatus(IncidentStatus.UNDERWAY);
                }
                // *주의: 여기서 incidentRepository.save(incident)는 영속성 컨텍스트에 의해 자동 처리되지만 명시해도 됨
            }
            kb.touchActivity();
        }

/*
        }
        if (kb.getCreatedBy() != CreatedBy.system && StringUtils.hasText(title) && StringUtils.hasText(content)) {
            kb.setStatus(KbStatus.RESPONDED);
            kb.setUpdatedAt(LocalDateTime.now());
            kb.touchActivity();

            if (kb.getIncident() != null) {
                kb.getIncident().setStatus(com.soyunju.logcollector.domain.kb.enums.IncidentStatus.RESOLVED);
            }
        } else {
            throw new IllegalArgumentException("게시(RESPONDED)에는 title 과 content가 모두 필요합니다.");
        }

 */

        /*
        // Status Update
        if (kb.getCreatedBy() != CreatedBy.system) {
            if (StringUtils.hasText(title) && StringUtils.hasText(content)) {
                kb.setStatus(KbStatus.RESPONDED); // 제목+내용 있으면 응답 완료
                kb.setUpdatedAt(LocalDateTime.now());
            } else {
                kb.setStatus(KbStatus.UNDERWAY); // 하나라도 부족하면 진행 중
            }
            kb.touchActivity();
        }

         */
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
