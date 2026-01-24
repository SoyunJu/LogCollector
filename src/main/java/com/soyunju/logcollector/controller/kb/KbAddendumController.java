package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbAddendumResponse;
import com.soyunju.logcollector.service.kb.crud.KbAddendumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbAddendumController {

    private final KbAddendumService kbAddendumService;

    // 메모 추가(=kb_addendum insert)
    @PostMapping("/{kbArticleId}/addendums")
    public ResponseEntity<KbAddendumResponse> createAddendum(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest req
    ) {
        return ResponseEntity.ok(kbAddendumService.createAddendum(kbArticleId, req));
    }
}
