package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.*;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.repository.kb.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KbArticleService {

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;
    private final KbAddendumRepository kbAddendumRepository;
    private final KbArticleTagRepository kbArticleTagRepository;
    private final KbTagRepository kbTagRepository;

    @Transactional
    public Long createDraftFromIncident(Long incidentId, CreatedBy creator) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        // 1. 이미 동일한 인시던트로 생성된 KB가 있는지 확인 (중복 방지)
        if (kbArticleRepository.existsByIncidentId(incidentId)) {
            return null;
        }

        // 2. Incident 데이터를 그대로 복사하여 KbArticle 생성 (AI 개입 없이 단순 필드 매핑)
        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(incident.g
                .content(generateDefaultContent(incident))
                .status(KbStatus.OPEN)
                .createdBy(creator)
                .build();

        return kbArticleRepository.save(kb).getId();
    }

    private String generateDefaultContent(Incident incident) {
        return "### [시스템 생성 초안]\n" +
                "**요약:** " + incident.getSummary() + "\n\n" +
                "**에러 로그:** \n```\n" + incident.getStackTrace() + "\n```";
    }

    @Transactional
    public Long createDraft(Long incidentId, String incidentTitle, String content, CreatedBy createdBy) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("incident not found: " + incidentId));

        String body = (content == null || content.isBlank())
                ? defaultTemplate(incident)
                : content;

        KbArticle kb = KbArticle.builder()
                .incident(incident)
                .incidentTitle(incidentTitle)
                .content(body)
                .status(KbStatus.OPEN)
                .confidenceLevel(1)
                .createdBy(createdBy == null ? CreatedBy.system : createdBy)
                .build();

        return kbArticleRepository.save(kb).getId();
    }

    @Transactional
    public void addAddendum(Long kbArticleId, String content, CreatedBy createdBy) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is blank");
        }

        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("kb_article not found: " + kbArticleId));

        KbAddendum add = KbAddendum.builder()
                .kbArticle(kb)
                .content(content)
                .createdBy(createdBy == null ? CreatedBy.user : createdBy)
                .build();

        kbAddendumRepository.save(add);

        // 활동 시간 갱신
        kb.touchActivity();

        // 최소 정책 예시: addendum 추가 시 RESPONDED로 전환(원하면 제거 가능)
        if (kb.getStatus() == KbStatus.OPEN) {
            kb.setStatus(KbStatus.RESPONDED);
        }
    }

    @Transactional
    public void setTags(Long kbArticleId, List<String> keywords) {
        // 전체 교체 방식(최소 구현)
        kbArticleTagRepository.deleteByKbArticleId(kbArticleId);

        if (keywords == null) return;

        for (String raw : keywords) {
            if (raw == null) continue;
            String keyword = raw.trim();
            if (keyword.isEmpty()) continue;

            KbTag tag = kbTagRepository.findByKeyword(keyword)
                    .orElseGet(() -> kbTagRepository.save(KbTag.builder().keyword(keyword).build()));

            kbArticleTagRepository.save(KbArticleTag.builder()
                    .kbArticleId(kbArticleId)
                    .kbTagId(tag.getId())
                    .build());
        }
    }

    @Transactional
    public KbArticleResponse getArticle(Long kbArticleId) {
        KbArticle kb = kbArticleRepository.findById(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("kb_article not found: " + kbArticleId));

        List<KbAddendum> addendums = kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtAsc(kbArticleId);

        List<KbArticleTag> mappings = kbArticleTagRepository.findByKbArticleId(kbArticleId);
        List<String> tags = new ArrayList<>();
        for (KbArticleTag m : mappings) {
            kbTagRepository.findById(m.getKbTagId()).ifPresent(t -> tags.add(t.getKeyword()));
        }

        return KbArticleResponse.builder()
                .id(kb.getId())
                .incidentId(kb.getIncident().getId())
                .incidentTitle(kb.getIncidentTitle())
                .content(kb.getContent())
                .status(kb.getStatus().name())
                .confidenceLevel(kb.getConfidenceLevel())
                .createdBy(kb.getCreatedBy().name())
                .lastActivityAt(kb.getLastActivityAt())
                .createdAt(kb.getCreatedAt())
                .tags(tags)
                .addendums(addendums.stream().map(a ->
                        KbArticleResponse.AddendumDto.builder()
                                .id(a.getId())
                                .content(a.getContent())
                                .createdBy(a.getCreatedBy().name())
                                .createdAt(a.getCreatedAt())
                                .build()
                ).toList())
                .build();
    }

    private String defaultTemplate(Incident incident) {
        // 최소 템플릿: 추후 AI/정규화로 교체
        return """
                ## 현상
                - 
                
                ## 영향 범위
                - service: %s
                - log_hash: %s
                
                ## 원인 추정
                - 
                
                ## 대응/해결
                - 
                
                ## 재발 방지
                - 
                """.formatted(incident.getServiceName(), incident.getLogHash());
    }
}
