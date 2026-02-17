package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbAddendumResponse;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.dto.kb.KbArticleSearch;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.service.kb.crud.KbCrudService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.kb.search.KbArticleSearchService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "04. KB Draft", description = "Draft 생성/누적/게시 + KB 상세 조회(= addendum 포함)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbCrudService kbCrudService;
    private final KbDraftService kbDraftService;
    private final KbArticleSearchService kbArticleSearchService;
    private final KbAddendumRepository kbAddendumRepository;

    @Operation(summary = "POST /api/kb/draft - Draft 생성", description = "Resolved된 incident 기반으로 Draft(KB article) 생성")
    @PostMapping("/draft")
    public ResponseEntity<Long> createDraft(@RequestParam Long incidentId) {
        return ResponseEntity.ok(kbDraftService.createSystemDraft(incidentId));
    }

    @Operation(
            summary = "GET /api/kb - KB list 조회",
            description = "KB list를 조회합니다."
    )
    @GetMapping
    public ResponseEntity<Page<KbArticleResponse>> getKbArticles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String createdBy,
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        KbArticleSearch kas = new KbArticleSearch();
        kas.setStatus(status);
        kas.setKeyword(keyword);
        kas.setCreatedBy(createdBy);
        return ResponseEntity.ok(kbArticleSearchService.findAll(kas, pageable));
    }

    @Operation(
            summary = "GET /api/kb/{kbArticleId} - KB 상세 조회",
            description = "Draft/KB 문서 상세를 조회합니다."
    )
    @GetMapping("/{kbArticleId}")
    public ResponseEntity<KbArticleResponse> getKbArticle(
            @PathVariable Long kbArticleId,
            @RequestParam(name = "addendumPage", required = false, defaultValue = "0") int addendumPage,
            @RequestParam(name = "addendumSize", required = false, defaultValue = "20") int addendumSize
    ) {
        return ResponseEntity.ok(kbArticleSearchService.getArticle(kbArticleId, addendumPage, addendumSize));
    }

    @Operation(summary = "POST /api/kb/articles/{kbArticleId} - 게시(내용 확정/승격)")
    @PostMapping("/articles/{kbArticleId}")
    public ResponseEntity<Void> postArticle(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {
        kbCrudService.postArticle(kbArticleId, request.getTitle(), request.getContent(), request.getCreatedBy());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "PATCH /api/kb/articles/{kbArticleId}/status - KB 상태 변경")
    @PatchMapping("/articles/{kbArticleId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long kbArticleId,
            @RequestParam KbStatus status) {
        kbCrudService.updateStatus(kbArticleId, status);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "POST /api/kb/draft/{kbArticleId}/update - 제목/작성자 수정")
    @PostMapping("/draft/{kbArticleId}/update")
    public ResponseEntity<Void> updateDraft(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest request) {

        kbCrudService.postArticle(
                kbArticleId,
                request.getTitle(),
                null,
                request.getCreatedBy()
        );
        return ResponseEntity.ok().build();
    }

    @Hidden
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
