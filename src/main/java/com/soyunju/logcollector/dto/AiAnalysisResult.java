package com.soyunju.logcollector.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiAnalysisResult {
    private String cause;      // AI가 분석한 원인
    private String suggestion; // AI가 제안하는 해결책
}
