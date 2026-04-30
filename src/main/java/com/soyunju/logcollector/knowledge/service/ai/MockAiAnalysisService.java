package com.soyunju.logcollector.service.kb.ai;

import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
//@Profile("dev") // 개발 환경에서 활성화
@Profile({"local", "docker", "dev", "test"})
public class MockAiAnalysisService implements AiAnalysisService {

    @Override
    public AiAnalysisResult AiAnalyze(Long incidentId) {
        // DB 조회 없이 고정된 결과 반환 (테스트 용도)
        return new AiAnalysisResult(
                "테스트용 분석 결과: DB 커넥션 풀 고갈 가능성이 높습니다.",
                "테스트용 권장 조치: HikariCP 설정을 점검하십시오."
        );
    }
}
