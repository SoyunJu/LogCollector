package com.soyunju.logcollector.knowledge.service.ai;

import com.soyunju.logcollector.knowledge.dto.AiAnalysisResult;


public interface AiAnalysisService {
    AiAnalysisResult AiAnalyze(Long incidentId);
}