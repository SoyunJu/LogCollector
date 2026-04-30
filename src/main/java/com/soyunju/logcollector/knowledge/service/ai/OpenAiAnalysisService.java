package com.soyunju.logcollector.service.kb.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("prod")
public class OpenAiAnalysisService implements AiAnalysisService {

    @Value("${openai.api.key}")
    private String apiKey;
    @Value("${openai.api.url}")
    private String apiUrl;
    @Value("${openai.model}")
    private String model;

    // [수정] ErrorLogRepository 의존성 제거 (Incident에 stackTrace가 이미 있음)
    private final RestClient restClient;
    private final AiRateLimiterService rateLimiterService;
    private final IncidentRepository incidentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AiAnalysisResult AiAnalyze(Long incidentId) {
        // 1. 호출 제한 체크
      /*  if (!rateLimiterService.tryConsume()) {
            throw new RuntimeException("AI 분석 요청 제한 초과 (잠시 후 다시 시도하세요)");
        } */
        // TODO :호출 LIMIT 구현 및 적용

        // 2. Incident 조회
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        // 3. 분석 데이터 준비 (Incident 내 필드 사용)
        String stackTrace = incident.getStackTrace();
        String summary = incident.getSummary();

        if (!StringUtils.hasText(stackTrace) && !StringUtils.hasText(summary)) {
            throw new IllegalStateException("분석할 정보가 없습니다. (stackTrace/summary 둘 다 비어있음)");
        }

        // 4. 프롬프트 구성 (JSON 형식 요청)
        String prompt = String.format("""
                Analyze the following error log and provide the Root Cause and Suggested Solution.
                
                [Log Summary]
                %s
                
                [Stack Trace]
                %s
                
                Output must be strictly in JSON format with the following keys:
                - "cause": A concise explanation of the root cause (Korean).
                - "suggestion": A step-by-step solution or debugging guide (Korean).
                """,
                summary != null ? summary : "",
                stackTrace != null ? stackTrace : ""
        );

        // 5. API 호출
        String jsonResponse = callOpenAiApi(prompt);

        // 6. 결과 파싱 (JSON -> DTO)
        try {
            return objectMapper.readValue(jsonResponse, AiAnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.error("AI Response JSON parsing failed. Response: {}", jsonResponse, e);
            return new AiAnalysisResult("파싱 실패", "AI 응답을 처리하는 중 오류가 발생했습니다.\n" + jsonResponse);
        }
    }

    private String callOpenAiApi(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a professional SRE engineer. You always output valid JSON."),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        Map<String, Object> response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null || !response.containsKey("choices")) {
            throw new RuntimeException("OpenAI API 응답이 올바르지 않습니다.");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) {
            throw new RuntimeException("OpenAI API 응답에 choice가 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}