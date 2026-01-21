package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.kb.SystemDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "kbTransactionManager")
public class KbCrdService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;
    private final SystemDraftRepository systemDraftRepository;

    // 사용자 입력 단계 (title-현상 필수, content-해결안은 nullable)
    @Transactional
    public void postArticle(Long kbArticleId, String title, String content) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 KB를 찾을 수 없습니다."));

        if (title != null && !title.isBlank()) {
            kb.setIncidentTitle(title);
            // incident 동기화
            if (kb.getIncident() != null) {
                kb.getIncident().setIncidentTitle(title);
            }
        }
        if (content != null) { kb.setContent(content); }

        if (kb.getCreatedBy() != CreatedBy.system) {
            if (org.springframework.util.StringUtils.hasText(title) && org.springframework.util.StringUtils.hasText(content)) {
                kb.setStatus(KbStatus.RESPONDED); // TODO : Draft 있을 시 삭제 로직 필요
                kb.setPublishedAt(LocalDateTime.now());
            } else {
                kb.setStatus(KbStatus.UNDERWAY); // TODO : Draft 삭제 로직 필요
            }
        } else {
            if (org.springframework.util.StringUtils.hasText(content)) {
                kb.setStatus(KbStatus.RESPONDED); // TODO : Draft 삭제 로직 필요
            }
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
