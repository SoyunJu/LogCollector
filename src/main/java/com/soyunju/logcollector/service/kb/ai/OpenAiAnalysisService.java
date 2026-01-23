package com.soyunju.logcollector.service.kb.ai;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.dto.kb.AiAnalysisResult;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
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
    private final IncidentRepository incidentRepository;

    @Override
    public AiAnalysisResult AiAnalyze(Long incidentId) {
        // 1. 호출 제한 체크
        if (!rateLimiterService.isAllowed()) {
            return new AiAnalysisResult("분석 실패: 시스템 전체 일일 AI 호출 제한에 도달했습니다.", "내일 다시 시도하십시오.");
        }

        // 2. 인시던트 데이터 조회
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("인시던트를 찾을 수 없습니다. ID: " + incidentId));

        // 3. 필수 필드 검증
        validateIncidentFields(incident);

        try {
            // 4. 프롬프트
            String prompt = String.format("당신은 전문 SRE 엔지니어입니다. 다음 에러 정보를 분석하세요.\\n" +
                            "요약: %s\\n 스택트레이스: %s",
                            incident.getSummary(), incident.getStackTrace());
                           // "에러코드: %s\\n요약: %s\\n스택트레이스: %s",
                        // incident.getErrorCode(), incident.getSummary(), incident.getStackTrace());

            // 5. API 호출 및 결과 파싱
            String rawContent = callOpenAiApi(prompt);
            return new AiAnalysisResult(parseResult(rawContent, "원인:"), parseResult(rawContent, "조치:"));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return new AiAnalysisResult("OpenAI API 할당량 초과", "잠시 후 다시 시도하거나 관리자에게 문의하십시오.");
            }
            return new AiAnalysisResult("분석 요청 오류 (" + e.getStatusCode() + ")", "입력 데이터 형식을 확인하십시오.");
        } catch (Exception e) {
            return new AiAnalysisResult("분석 중 서버 오류 발생", "시스템 로그를 확인하십시오.");
        }
    }

    private void validateIncidentFields(Incident incident) {
        // if (incident.getErrorCode() == null) throw new IllegalStateException("errorCode 필드가 null입니다.");
        if (incident.getSummary() == null) throw new IllegalStateException("summary 필드가 null입니다.");
        if (incident.getStackTrace() == null) throw new IllegalStateException("StackTrace 필드가 null입니다.");
    }

    private String callOpenAiApi(String prompt) {
        Map<String, Object> response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "messages", List.of(
                        Map.of("role", "system", "content", "You are a professional SRE engineer."),
                        Map.of("role", "user", "content", prompt)
                )))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

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
