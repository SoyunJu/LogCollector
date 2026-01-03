package com.soyunju.logcollector.service.ai;

import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiAnalysisService {

    @Value("${openai.api.key}") private String apiKey;
    @Value("${openai.api.url}") private String apiUrl;
    @Value("${openai.model}") private String model;

    private final ErrorLogRepository errorLogRepository;
    private final RestClient restClient;

    // 반환 타입을 void에서 AiAnalysisResult로 변경
    public AiAnalysisResult openAiAnalysis(Long logId) {
        // 1. 프로젝션을 이용해 필요한 데이터만 효율적으로 조회
        ErrorLogRepository.LogAnalysisData data = errorLogRepository.findAnalysisDataById(logId)
                .orElseThrow(() -> new IllegalArgumentException("분석할 데이터를 찾을 수 없습니다. ID: " + logId));

        // 2. 요구사항: 필수 필드 null 체크 및 상세 예외 발생
        validateRequiredFields(data);

        // 3. AI 분석 수행
        String rawContent = callOpenAiApi(data);

        // 4. 분석 결과 객체 생성 및 반환
        return new AiAnalysisResult(
                parseResult(rawContent, "원인:"),
                parseResult(rawContent, "조치:")
        );
    }

    private void validateRequiredFields(ErrorLogRepository.LogAnalysisData data) {
        if (data.getErrorCode() == null) throw new IllegalStateException("errorCode 필드가 null입니다.");
        if (data.getSummary() == null) throw new IllegalStateException("summary 필드가 null입니다.");
        if (data.getMessage() == null) throw new IllegalStateException("message 필드가 null입니다.");
    }

    private String callOpenAiApi(ErrorLogRepository.LogAnalysisData data) {
        // AI가 반드시 형식을 지키도록 프롬프트 강화
        String prompt = String.format(
                "당신은 전문 SRE 엔지니어입니다. 다음 에러 정보를 분석하세요.\n" +
                        "에러코드: %s\n요약: %s\n메시지: %s\n\n" +
                        "응답은 반드시 아래 형식을 엄격히 지켜야 하며, 다른 부연 설명은 하지 마세요.\n" +
                        "원인: [상세한 발생 원인 한 줄]\n" +
                        "조치: [구체적인 단계별 해결 방법]",
                data.getErrorCode(), data.getSummary(), data.getMessage());

        Map response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", "You are a professional system analyst."),
                                Map.of("role", "user", "content", prompt)
                        )
                ))
                .retrieve()
                .body(Map.class);

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