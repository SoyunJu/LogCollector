package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.KbAddendum;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
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

    private final KbArticleRepository kbArticleRepository;
    private final KbAddendumRepository kbAddendumRepository;

    // 사용자 입력 단계 (title-현상 필수, content-해결안은 nullable)
    @Transactional
    public void postArticle(Long kbArticleId, String title, String content, String createdBy) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 KB를 찾을 수 없습니다."));

        // ARCHIVED는 upsert 불가
        if (kb.getStatus() == KbStatus.ARCHIVED) {
            throw new IllegalStateException("ARCHIVED 상태에서는 추가 작성할 수 없습니다. kbArticleId=" + kbArticleId);
        }

        // createdBy 파싱(기본 user) // TODO : USER 를 로그인 된 ID 로 가져오기
        CreatedBy cb = CreatedBy.user;
        if (StringUtils.hasText(createdBy)) {
            try {
                cb = CreatedBy.valueOf(createdBy.toLowerCase());
            } catch (IllegalArgumentException ignore) {
            }
        }

        // Title 동기화
        if (StringUtils.hasText(title)) {
            kb.setIncidentTitle(title);
            if (kb.getIncident() != null) {
                kb.getIncident().setIncidentTitle(title);
            }
        }

        if (StringUtils.hasText(content)) {
            kb.setContent(content);

            kbAddendumRepository.save(
                    KbAddendum.builder()
                            .kbArticle(kb)
                            .content(content)
                            .createdBy(cb)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
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
            if (kb.getStatus() != KbStatus.PUBLISHED) {
                kb.setStatus(KbStatus.IN_PROGRESS);
            }
        }
        kb.setCreatedBy(cb);
        kb.touchActivity();
        kbArticleRepository.save(kb);
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
