package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import com.soyunju.logcollector.service.kb.crd.KbDraftService;
import com.soyunju.logcollector.service.kb.search.KbArticleSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbArticleService kbArticleService;
    private final KbDraftService kbDraftService;
    private final KbArticleSearchService kbArticleSearchService;

    // LC 에서 resolved 된 log 처리
    @PostMapping("/draft")
    public ResponseEntity<Long> createDraft(@RequestParam Long incidentId) {
        return ResponseEntity.ok(kbDraftService.createSystemDraft(incidentId));
    }

    // 시스템 자동 draft
   /*  @PostMapping("/posting")
    public ResponseEntity<Void> postArticle(@RequestParam Long kbArticleId, @RequestParam String title, @RequestParam(required = false) String content) {
        kbDraftService.postArticle(kbArticleId, title, content);
        return ResponseEntity.ok().build();
    } */

    // KB 초안 목록 조회 (index.html loadKb 대응)
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.KbArticleResponse>> getKbArticles(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(kbArticleSearchService.findAll(pageable));
    }

    // KB 상세 조회
    @GetMapping("/{kbArticleId}")
    public ResponseEntity<KbArticleResponse> getKbArticle(@PathVariable Long kbArticleId) {
        return ResponseEntity.ok(kbArticleSearchService.getArticle(kbArticleId));
    }

    // 사용자 KB Post
    @PostMapping("/articles/{kbArticleId}")
    public ResponseEntity<Void> postArticle(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {
        kbArticleService.postArticle(kbArticleId, request.getTitle(), request.getContent());
        return ResponseEntity.ok().build();
    }

    // Draft 변경용 POST
    @PostMapping("/drafts/{kbArticleId}")
    public ResponseEntity<Void> postDraft(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {
        kbDraftService.postDraft(kbArticleId, request.getTitle(), request.getContent());
        return ResponseEntity.ok().build();
    }


}