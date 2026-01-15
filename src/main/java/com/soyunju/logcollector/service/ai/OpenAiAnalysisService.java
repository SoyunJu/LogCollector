package com.soyunju.logcollector.service.ai;

import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.dto.LogAnalysisData;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Profile("prod") // 운영 환경에서만 활성화
public class OpenAiAnalysisService implements AiAnalysisService {

    @Value("${openai.api.key}")
    private String apiKey;
    @Value("${openai.api.url}")
    private String apiUrl;
    @Value("${openai.model}")
    private String model;

    private final ErrorLogRepository errorLogRepository;
    private final RestClient restClient;
    private final AiRateLimiterService rateLimiterService;

    @Override
    public AiAnalysisResult analyze(Long logId) {
        // 1. 호출 제한 체크
        if (!rateLimiterService.isAllowed()) {
            return new AiAnalysisResult("분석 실패: 시스템 전체 일일 AI 호출 제한에 도달했습니다.", "내일 다시 시도하거나 관리자에게 문의하십시오.");
        }
        // 2. 데이터 조회
        LogAnalysisData data = errorLogRepository.findAnalysisDataById(logId).orElseThrow(() -> new IllegalArgumentException("분석 데이터를 찾을 수 없습니다. ID: " + logId));
        // 3. 필수 필드 검증
        validateRequiredFields(data);
        try {
            // 4. API 호출 및 결과 파싱
            String rawContent = callOpenAiApi(data);
            return new AiAnalysisResult(parseResult(rawContent, "원인:"), parseResult(rawContent, "조치:"));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return new AiAnalysisResult("OpenAI API 할당량 초과", "잠시 후 다시 시도하십시오.");
            }
            return new AiAnalysisResult("분석 요청 오류 (" + e.getStatusCode() + ")", "입력 데이터 형식을 확인하십시오.");
        } catch (Exception e) {
            return new AiAnalysisResult("분석 중 서버 오류 발생", "시스템 로그를 확인하십시오.");
        }
    }

    private void validateRequiredFields(LogAnalysisData data) {
        if (data.errorCode() == null) throw new IllegalStateException("errorCode 필드가 null입니다.");
        if (data.summary() == null) throw new IllegalStateException("summary 필드가 null입니다.");
        if (data.message() == null) throw new IllegalStateException("message 필드가 null입니다.");
    }

    private String callOpenAiApi(LogAnalysisData data) {
        // AI가 반드시 형식을 지키도록 프롬프트 강화
        String prompt = String.format("당신은 전문 SRE 엔지니어입니다. 다음 에러 정보를 분석하세요.\n" + "에러코드: %s\n요약: %s\n메시지: %s\n\n" + "응답은 반드시 아래 형식을 엄격히 지켜야 하며, 다른 부연 설명은 하지 마세요.\n" + "원인: [상세한 발생 원인 한 줄]\n" + "조치: [구체적인 단계별 해결 방법]", data.errorCode(), data.summary(), data.message());

        Map response = restClient.post().uri(apiUrl).header("Authorization", "Bearer " + apiKey).body(Map.of("model", model, "messages", List.of(Map.of("role", "system", "content", "You are a professional system analyst."), Map.of("role", "user", "content", prompt)))).retrieve().body(Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String parseResult(String raw, String type) {
        try {
            if (type.equals("원인:")) {
                // "원인:" 이후부터 "조치:" 직전까지 추출
                int start = raw.indexOf("원인:") + 3;
                int end = raw.indexOf("조치:");
                return raw.substring(start, end).trim();
            } else {
                // "조치:" 이후부터 끝까지 추출
                int start = raw.indexOf("조치:") + 3;
                return raw.substring(start).trim();
            }
        } catch (Exception e) {
            // 파싱 실패 시 원본의 일부라도 반환하거나 에러 메시지 반환
            return "분석 결과 추출 실패: " + raw;
        }
    }
}
