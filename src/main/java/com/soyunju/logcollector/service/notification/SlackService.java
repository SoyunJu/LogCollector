package com.soyunju.logcollector.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private final RestClient restClient;

    @Value("${slack.webhook.url}")
    private String webhookUrl;

    public void sendErrorNotification(String title, String serviceName, String summary, long impactedHostCount) {
        String message = String.format(
                "%s \n" + // 🚨 [신규 에러] 등의 제목이 들어감
                        "• *서비스명*: %s\n" +
                        "• *요약*: %s\n" +
                        "• *영향 받는 서버 수*: %d대",
                title, serviceName, summary, impactedHostCount
        );

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Slack 전송 실패: {}", e.getMessage());
        }
    }
}