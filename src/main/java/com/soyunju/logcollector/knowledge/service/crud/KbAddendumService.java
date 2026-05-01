package com.soyunju.logcollector.knowledge.service.crud;


import com.soyunju.logcollector.knowledge.domain.KbAddendum;
import com.soyunju.logcollector.knowledge.domain.KbArticle;
import com.soyunju.logcollector.knowledge.domain.enums.CreatedBy;
import com.soyunju.logcollector.knowledge.dto.KbAddendumCreateRequest;
import com.soyunju.logcollector.knowledge.dto.KbAddendumResponse;
import com.soyunju.logcollector.knowledge.repository.KbAddendumRepository;
import com.soyunju.logcollector.knowledge.repository.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "kbTransactionManager")
public class KbAddendumService {

    private final KbArticleRepository kbArticleRepository;
    private final KbAddendumRepository kbAddendumRepository;

    public KbAddendumResponse createAddendum(Long kbArticleId, KbAddendumCreateRequest req) {
        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }

        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("KBArticle을 찾을 수 없습니다. id=" + kbArticleId));

        CreatedBy cb = parseCreatedByOrDefault(req.getCreatedBy());

        KbAddendum saved = kbAddendumRepository.save(
                KbAddendum.builder()
                        .kbArticle(kb)
                        .content(req.getContent())
                        .createdBy(cb)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        return KbAddendumResponse.from(saved);
    }

    private CreatedBy parseCreatedByOrDefault(String raw) {
        if (raw == null || raw.isBlank()) return CreatedBy.system;
        try {
            return CreatedBy.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // 프론트에서 임의 문자열 보내도 서버가 죽지 않게 system으로 처리
            return CreatedBy.system;
        }
    }
}
