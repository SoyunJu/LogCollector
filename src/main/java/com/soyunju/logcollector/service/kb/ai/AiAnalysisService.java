package com.soyunju.logcollector.service.kb.ai;

import com.soyunju.logcollector.dto.kb.AiAnalysisResult;


public interface AiAnalysisService {
    // 로그 ID를 받아 분석 결과를 반환하는 표준 메서드
//    AiAnalysisResult analyze(Long logId);

    AiAnalysisResult AiAnalyze(Long incidentId);
}