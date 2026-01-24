package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbAddendumResponse;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.dto.kb.KbArticleSearch;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.service.kb.crud.KbCrudService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.kb.search.KbArticleSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbCrudService kbCrudService;
    private final KbDraftService kbDraftService;
    private final KbArticleSearchService kbArticleSearchService;
    private final KbAddendumRepository kbAddendumRepository;

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

    // KB 상세 조회 => addendums 포함
    @GetMapping("/{kbArticleId}")
    public ResponseEntity<KbArticleResponse> getKbArticle(
            @PathVariable Long kbArticleId,
            @RequestParam(name = "addendumPage", required = false, defaultValue = "0") int addendumPage,
            @RequestParam(name = "addendumSize", required = false, defaultValue = "20") int addendumSize
    ) {
        return ResponseEntity.ok(kbArticleSearchService.getArticle(kbArticleId, addendumPage, addendumSize));
    }

    // 사용자 KB Addendum Post
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

        kbDraftService.updateDraft(
                kbArticleId,
                request.getTitle(),
                request.getContent(),
                request.getCreatedBy()
        );

        return ResponseEntity.ok().build();
    }

    // KB Addendum 목록 조회 desc
    @GetMapping("/articles/{kbArticleId}/addendums")
    public ResponseEntity<List<KbAddendumResponse>> getAddendums(@PathVariable Long kbArticleId) {
        return ResponseEntity.ok(
                kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtDesc(kbArticleId)
                        .stream()
                        .map(KbAddendumResponse::from)
                        .toList()
        );
    }

}