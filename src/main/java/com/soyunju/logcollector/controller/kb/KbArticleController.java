package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.dto.kb.KbDraftCreateRequest;
import com.soyunju.logcollector.service.kb.KbArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kb")
public class KbArticleController {

    private final KbArticleService kbArticleService;

    @PostMapping("/incidents/{incidentId}/draft")
    public ResponseEntity<Long> createDraft(@PathVariable Long incidentId,
                                            @RequestBody KbDraftCreateRequest req) {
        // created_by는 최소 구현: system 고정
        Long kbArticleId = kbArticleService.createDraft(
                incidentId,
                req.getIncidentTitle(),
                req.getContent(),
                CreatedBy.system
        );
        return ResponseEntity.ok(kbArticleId);
    }

    @PostMapping("/articles/{kbArticleId}/addendum")
    public ResponseEntity<Void> addAddendum(@PathVariable Long kbArticleId,
                                            @RequestBody KbAddendumCreateRequest req) {
        kbArticleService.addAddendum(kbArticleId, req.getContent(), CreatedBy.user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/articles/{kbArticleId}/tags")
    public ResponseEntity<Void> setTags(@PathVariable Long kbArticleId,
                                        @RequestBody List<String> keywords) {
        kbArticleService.setTags(kbArticleId, keywords);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/articles/{kbArticleId}")
    public ResponseEntity<KbArticleResponse> getArticle(@PathVariable Long kbArticleId) {
        return ResponseEntity.ok(kbArticleService.getArticle(kbArticleId));
    }
}
