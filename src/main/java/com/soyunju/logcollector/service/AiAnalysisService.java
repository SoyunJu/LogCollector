package com.soyunju.logcollector.service;

public interface AiAnalysisService {
    /**
     * 에러 메시지와 요약된 로그를 바탕으로 원인 및 조치 방안 분석
     */
    String[] analyze(String message, String summary);
}