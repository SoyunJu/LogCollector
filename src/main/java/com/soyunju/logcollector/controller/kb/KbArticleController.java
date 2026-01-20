package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbArticleService kbArticleService;

    // LC 에서 resolved 된 log 처리
    @PostMapping("/draft")
    public ResponseEntity<Long> createDraft(@RequestParam Long incidentId) {
        return ResponseEntity.ok(kbArticleService.createSystemDraft(incidentId));
    }

    // 시스템 자동 draft
    @PostMapping("/posting")
    public ResponseEntity<Void> postArticle(@RequestParam Long kbArticleId, @RequestParam String title, @RequestParam(required = false) String content) {
        kbArticleService.postArticle(kbArticleId, title, content);
        return ResponseEntity.ok().build();
    }

    // [추가] KB 초안 목록 조회 (index.html loadKb 대응)
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.KbArticleResponse>> getKbArticles(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(kbArticleService.findAll(pageable));
    }

}