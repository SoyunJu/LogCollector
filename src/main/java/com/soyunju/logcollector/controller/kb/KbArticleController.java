package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.dto.kb.KbArticleSearch;
import com.soyunju.logcollector.service.kb.crud.KbCrudService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.kb.search.KbArticleSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbCrudService kbCrudService;
    private final KbDraftService kbDraftService;
    private final KbArticleSearchService kbArticleSearchService;

    // LC resolved -> Draft 생성
    @PostMapping("/draft")
    public ResponseEntity<Long> createDraft(@RequestParam Long incidentId) {
        return ResponseEntity.ok(kbDraftService.createSystemDraft(incidentId));
    }

    // KB Draft List 조회
    @GetMapping
    public ResponseEntity<Page<KbArticleResponse>> getKbArticles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String createdBy,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        KbArticleSearch kas = new KbArticleSearch();
        kas.setStatus(status);
        kas.setKeyword(keyword);
        kas.setCreatedBy(createdBy);

        return ResponseEntity.ok(kbArticleSearchService.findAll(kas, pageable));
    }

    // KB 상세 조회
    @GetMapping("/{kbArticleId}")
    public ResponseEntity<KbArticleResponse> getKbArticle(@PathVariable Long kbArticleId) {
        return ResponseEntity.ok(kbArticleSearchService.getArticle(kbArticleId));
    }

    // 사용자 KB Post , TODO : Pub 누르면 KB 생성
    @PostMapping("/articles/{kbArticleId}")
    public ResponseEntity<Void> postArticle(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {
        kbCrudService.postArticle(kbArticleId, request.getTitle(), request.getContent(), request.getCreatedBy() );
        return ResponseEntity.ok().build();
    }

    // Draft Update
    @PostMapping("/draft/{kbArticleId}")
    public ResponseEntity<Void> updateDraft(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {
        kbDraftService.updateDraft(kbArticleId, request.getTitle(), request.getContent());
        return ResponseEntity.ok().build();
    }


}