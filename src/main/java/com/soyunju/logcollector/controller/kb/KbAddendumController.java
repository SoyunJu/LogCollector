package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.KbAddendumCreateRequest;
import com.soyunju.logcollector.dto.kb.KbAddendumResponse;
import com.soyunju.logcollector.service.kb.crud.KbAddendumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "04. KB Draft", description = "Incident 해결 → Draft 생성/누적 → KB 게시(승격)")
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbAddendumController {

    private final KbAddendumService kbAddendumService;

    @Operation(
            summary = "POST /api/kb/{kbArticleId}/addendums - Addendum 추가",
            description = "Draft/KB 문서에 현상/해결 방안(Addendum)를 누적합니다."
    )
    @PostMapping("/{kbArticleId}/addendums")
    public ResponseEntity<KbAddendumResponse> createAddendum(
            @PathVariable Long kbArticleId,
            @RequestBody KbAddendumCreateRequest req
    ) {
        return ResponseEntity.ok(kbAddendumService.createAddendum(kbArticleId, req));
    }
}
