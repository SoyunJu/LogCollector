package com.soyunju.logcollector.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary // 실제 API 연동 전까지 이 클래스를 우선 사용
public class MockAiAnalysisService implements AiAnalysisService {
    @Override
    public String[] analyze(String message, String summary) {
        // 실제로는 여기서 외부 LLM API를 호출하게 됩니다.
        return new String[] {
                "분석 결과: " + message + "는 주로 DB 커넥션 풀 고갈 시 발생합니다.",
                "권장 조치: hikariCP 설정을 점검하고 커넥션 누수를 확인하세요."
        };
    }
}