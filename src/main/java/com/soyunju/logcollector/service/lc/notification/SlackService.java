package com.soyunju.logcollector.service.lc.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private final RestClient restClient;

    @Value("${slack.webhook.url:}")
    private String webhookUrl;

    private URI webhookUri;

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("Slack webhook URL이 설정되지 않았습니다. (slack.webhook.url 비어있음) Slack 알림은 비활성화됩니다.");
            return;
        }

        String u = webhookUrl.trim();

        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }

        try {
            this.webhookUri = URI.create(u);
        } catch (Exception e) {
            // 잘못된 URI면 비활성화
            log.error("Slack webhook URL이 올바른 URI 형식이 아닙니다. slack.webhook.url='{}' Slack 알림 비활성화.", webhookUrl);
            this.webhookUri = null;
        }
    }

    public void sendErrorNotification(String title, String serviceName, String summary, long impactedHostCount) {
        if (webhookUri == null) {
            return;
        }

        String message = String.format(
                "%s \n" +
                        "• *서비스명*: %s\n" +
                        "• *요약*: %s\n" +
                        "• *영향 받는 서버 수*: %d대",
                title, serviceName, summary, impactedHostCount
        );

        try {
            restClient.post()
                    .uri(webhookUri)
                    .body(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Slack 전송 실패: {}", e.getMessage());
        }
    }
}
