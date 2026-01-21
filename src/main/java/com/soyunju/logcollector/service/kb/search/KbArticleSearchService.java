package com.soyunju.logcollector.service.kb.search;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbArticleSearchService {

    private final KbArticleRepository kbArticleRepository;

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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public KbArticleResponse getArticle(Long kbArticleId) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("Base를 찾을 수 없습니다."));

        return KbArticleResponse.builder()
                .id(kb.getId())
                .incidentId(kb.getIncident().getId())
                .incidentTitle(kb.getIncidentTitle())
                .content(kb.getContent())
                .status(kb.getStatus().name())
                .createdAt(kb.getCreatedAt())
                .build();
    }



}
