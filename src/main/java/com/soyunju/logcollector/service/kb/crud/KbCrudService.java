package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.domain.kb.KbAddendum;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.service.kb.autopolicy.KbConfidenceCalculator;
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
    private final KbConfidenceCalculator confidenceCalculator;
    private final KbArticleEsService kbArticleEsService;

    // 사용자 입력 단계 (Title 수정 및 Addendum 추가)
    @Transactional(transactionManager = "kbTransactionManager")
    public void postArticle(Long kbArticleId, String title, String content, String createdBy) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 KB를 찾을 수 없습니다."));

        // ARCHIVED는 upsert 불가
        if (kb.getStatus() == KbStatus.ARCHIVED) {
            throw new IllegalStateException("ARCHIVED 상태에서는 추가 작성할 수 없습니다. kbArticleId=" + kbArticleId);
        }

        // createdBy 파싱(기본 user)
        CreatedBy cb = CreatedBy.user;
        if (StringUtils.hasText(createdBy)) {
            try {
                cb = CreatedBy.valueOf(createdBy.toUpperCase());
            } catch (IllegalArgumentException ignore) {
                // 파싱 실패 시 기본값 유지
            }
        }

        // Title 동기화
        if (StringUtils.hasText(title)) {
            kb.setIncidentTitle(title);
            if (kb.getIncident() != null) {
                kb.getIncident().setIncidentTitle(title);
            }
        }

        // Content가 넘어오면 Addendum(댓글)으로 저장 (KB 본문은 덮어쓰지 않음)
        if (StringUtils.hasText(content)) {
            //  DRAFT 상태에서 addednum 등록 시 IN_PROGRESS로 자동 전환
            if (kb.getStatus() == KbStatus.DRAFT) {
                kb.setStatus(KbStatus.IN_PROGRESS);
            }
            kbAddendumRepository.save(
                    KbAddendum.builder()
                            .kbArticle(kb)
                            .content(content)
                            .createdBy(cb) // Parsed Enum
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }

        // KB 메타데이터 갱신
        kb.setUpdatedAt(LocalDateTime.now());
        kb.setLastActivityAt(LocalDateTime.now());

        // ES indexing
        refreshConfidence(kb);
        kbArticleEsService.index(kb);
    }

    // KB 상태 변경
    public void updateStatus(Long kbArticleId, KbStatus status) {
        KbArticle kbArticle = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("KB Article not found"));

        //  PUBLISHED 전환 시 필수 조건 검증
        if (status == KbStatus.PUBLISHED) {
            // 1. 제목 확인
            if (!StringUtils.hasText(kbArticle.getIncidentTitle())) {
                throw new IllegalStateException("제목(Title)이 입력되지 않아 발행할 수 없습니다.");
            }

            // 2. Addendum 존재 여부 체크
            boolean hasAddendum = kbAddendumRepository.existsByKbArticle_Id(kbArticleId);
            if (!hasAddendum) {
                throw new IllegalStateException("해결 내용(Addendum)이 작성되지 않아 발행할 수 없습니다. 댓글로 해결 방법을 남겨주세요.");
            }
            kbArticle.setPublishedAt(LocalDateTime.now());
        }
        kbArticle.setStatus(status);
        kbArticle.setUpdatedAt(LocalDateTime.now());

        // ES
        refreshConfidence(kbArticle);
        kbArticleEsService.index(kbArticle);
    }


    // Helper Method

    // level 반영 메서드
    private void refreshConfidence(KbArticle kb) {
        int addendumCount = (int) kbAddendumRepository.countByKbArticle_Id(kb.getId());
        int repeatCount = (kb.getIncident() != null && kb.getIncident().getRepeatCount() != null)
                ? kb.getIncident().getRepeatCount() : 0;
        var errorLevel = (kb.getIncident() != null) ? kb.getIncident().getErrorLevel() : null;
        var lastOccurredAt = (kb.getIncident() != null) ? kb.getIncident().getLastOccurredAt() : null;

        int level = confidenceCalculator.calculate(kb, addendumCount, repeatCount, errorLevel, lastOccurredAt);
        kb.setConfidenceLevel(level);
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