package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs/analyze")
@RequiredArgsConstructor
public class AnalysisController {

    private final IncidentService incidentService;

    // 특정 로그 ID에 대한 AI 분석 요청
   /*  @PostMapping("/{logId}/analyze")
    public ResponseEntity<AiAnalysisResult> startAiAnalysis(@PathVariable Long logId) {
        return ResponseEntity.ok(aiAnalysisService.analyze(logId));
    } */

    @PostMapping("/{logHash}")
    public ResponseEntity<AiAnalysisResult> analyzeIncident(
            @PathVariable String logHash,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(incidentService.analyze(logHash, force));
    }
}