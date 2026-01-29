package com.soyunju.logcollector.dto.kb;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AiAnalysisResult {
    private String cause;      // AI가 분석한 원인
    private String suggestion; // AI가 제안하는 해결책
    private Long kbId;         // 연관된 KB ID (없으면 null)

    public AiAnalysisResult(String cause, String suggestion) {
        this(cause, suggestion, null);
    }
}
