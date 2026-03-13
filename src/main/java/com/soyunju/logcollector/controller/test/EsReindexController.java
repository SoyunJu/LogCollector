package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test/es")
@RequiredArgsConstructor
public class EsReindexController {

    private final KbArticleRepository kbArticleRepository;
    private final KbArticleEsService kbArticleEsService;

    @PostMapping("/reindex")
    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public ResponseEntity<Map<String, Object>> reindexAll() {
        List<KbArticle> all = kbArticleRepository.findAll();

        int success = 0;
        int failed = 0;

        for (KbArticle kb : all) {
            try {
                kbArticleEsService.index(kb);
                success++;
            } catch (Exception e) {
                log.warn("[ES][REINDEX] 실패 kbArticleId={}", kb.getId(), e);
                failed++;
            }
        }

        log.info("[ES][REINDEX] 완료 total={} success={} failed={}", all.size(), success, failed);

        return ResponseEntity.ok(Map.of(
                "total", all.size(),
                "success", success,
                "failed", failed
        ));
    }
}