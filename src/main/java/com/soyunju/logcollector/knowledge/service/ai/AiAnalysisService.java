package com.soyunju.logcollector.service.kb.ai;

import com.soyunju.logcollector.dto.kb.AiAnalysisResult;


public interface AiAnalysisService {
    AiAnalysisResult AiAnalyze(Long incidentId);
}