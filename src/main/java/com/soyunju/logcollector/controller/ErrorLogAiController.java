package com.soyunju.logcollector.controller;

import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.service.ai.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs/ai")
@RequiredArgsConstructor
public class ErrorLogAiController {

    private final AiAnalysisService aiAnalysisService;

    // 특정 로그 ID에 대한 AI 분석 요청
    @PostMapping("/{logId}/analyze")
    public ResponseEntity<AiAnalysisResult> startAiAnalysis(@PathVariable Long logId) {
        return ResponseEntity.ok(aiAnalysisService.analyze(logId));
    }
}