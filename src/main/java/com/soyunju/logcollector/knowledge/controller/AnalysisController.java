package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "03. Analysis", description = "Incident(logHash) 기반 분석 요청 (Stacktrace 중심)")
@RestController
@RequestMapping("/api/logs/analyze")
@RequiredArgsConstructor
public class AnalysisController {

    private final IncidentService incidentService;

    @Operation(
            summary = "POST /api/logs/analyze/{logHash} - 분석 요청",
            description = "logHash 기준 분석 결과를 생성/조회합니다. force=true면 기존 결과(kb)가 있어도 ai분석을 시도합니다."
    )
    @PostMapping("/{logHash}")
    public ResponseEntity<AiAnalysisResult> analyzeIncident(
            @PathVariable String logHash,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(incidentService.analyze(logHash, force));
    }
}
