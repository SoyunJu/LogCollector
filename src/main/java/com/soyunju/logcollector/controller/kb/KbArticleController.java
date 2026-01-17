package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}