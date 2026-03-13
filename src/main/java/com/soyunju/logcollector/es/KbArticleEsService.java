package com.soyunju.logcollector.es;

import com.soyunju.logcollector.domain.kb.KbAddendum;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbArticleEsService {

    private final KbArticleEsRepository esRepository;
    private final KbAddendumRepository kbAddendumRepository;
    private final KbArticleRepository kbArticleRepository;

    // WHEN : KbArticle upsert, THEN : ES indexing
    public void index(KbArticle kb) {
        try {
            // addendum 을 TEXT로 합치기
            List<KbAddendum> addendums = kbAddendumRepository
                    .findByKbArticle_IdOrderByCreatedAtDesc(kb.getId());

            String addendumText = addendums.stream()
                    .map(KbAddendum::getContent)
                    .collect(Collectors.joining("\n"));

            KbArticleDocument doc = KbArticleDocument.builder()
                    .id(String.valueOf(kb.getId()))
                    .incidentTitle(kb.getIncidentTitle())
                    .content(kb.getContent())
                    .addendumContent(addendumText)
                    .status(kb.getStatus() != null ? kb.getStatus().name() : null)
                    .serviceName(kb.getIncident() != null ? kb.getIncident().getServiceName() : null)
                    .errorCode(kb.getIncident() != null ? kb.getIncident().getErrorCode() : null)
                    .createdAt(kb.getCreatedAt())
                    .updatedAt(kb.getUpdatedAt())
                    .build();

            esRepository.save(doc);
            log.debug("[ES] KbArticle indexed: id={}", kb.getId());

        } catch (Exception e) {
            log.warn("[ES] 인덱싱 실패 (무시) kbArticleId={}, error={}", kb.getId(), e.getMessage());
        }
    }

    // 추후 사용 가능성 있어 유지
    public void delete(Long kbArticleId) {
        try {
            esRepository.deleteById(String.valueOf(kbArticleId));
        } catch (Exception e) {
            log.warn("[ES] 삭제 실패 (무시) kbArticleId={}, error={}", kbArticleId, e.getMessage());
        }
    }

    // 스케줄러용: updatedAt 기준 최근 변경분만 재인덱싱
    public int reindexSince(LocalDateTime since) {
        List<KbArticle> targets = kbArticleRepository.findUpdatedSince(since);
        int success = 0;
        for (KbArticle kb : targets) {
            try {
                index(kb); // 같은 클래스 내 메서드이므로 정상 호출
                success++;
            } catch (Exception e) {
                log.warn("[ES][REINDEX] 실패 kbArticleId={}, err={}", kb.getId(), e.getMessage());
            }
        }
        if (!targets.isEmpty()) {
            log.info("[ES][REINDEX] 완료 target={} success={}", targets.size(), success);
        }
        return success;
    }

    // 키워드로 KB_id 반환 -> JPA search
    public List<Long> searchIds(String keyword, int page, int size) {
        Page<KbArticleDocument> result = esRepository
                .findByIncidentTitleContainingOrContentContainingOrAddendumContentContaining(
                        keyword, keyword, keyword,
                        PageRequest.of(page, size)
                );

        return result.getContent().stream()
                .map(doc -> Long.parseLong(doc.getId()))
                .collect(Collectors.toList());
    }
}