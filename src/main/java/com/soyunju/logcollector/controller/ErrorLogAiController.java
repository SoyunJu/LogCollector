package com.soyunju.logcollector.controller;

import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.service.ai.ErrorLogAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs/ai") // AI 관련 경로는 명확히 구분
@RequiredArgsConstructor
public class ErrorLogAiController {

    private final ErrorLogAiService errorLogAiService;

    // 특정 로그 ID에 대한 AI 분석 요청
    @PostMapping("/{logId}/analyze")
    public ResponseEntity<AiAnalysisResult> startAiAnalysis(@PathVariable Long logId) {
        return ResponseEntity.ok(errorLogAiService.startAiAnalysis(logId));
    }
}