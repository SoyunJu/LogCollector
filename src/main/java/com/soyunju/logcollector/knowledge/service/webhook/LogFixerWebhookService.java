package com.soyunju.logcollector.service.logfixer;

import com.soyunju.logcollector.dto.logfixer.LogFixerIncidentPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogFixerWebhookService {

    private final RestClient restClient;

    @Value("${logfixer.webhook.url:}")
    private String webhookUrl;

    private URI webhookUri;

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("LogFixer webhook URL이 설정되지 않았습니다. (logfixer.webhook.url 비어있음) LogFixer 연동은 비활성화됩니다.");
            return;
        }

        try {
            this.webhookUri = URI.create(webhookUrl.trim());
        } catch (Exception e) {
            log.error("LogFixer webhook URL이 올바른 URI 형식이 아닙니다. logfixer.webhook.url='{}' 비활성화.", webhookUrl);
            this.webhookUri = null;
        }
    }

    // LogFixer 에 incident 비동기 발송 (실패해도 LC에 영향X)
    public void sendIncident(LogFixerIncidentPayload payload) {
        if (webhookUri == null) {
            return;
        }
        try {
            restClient.post()
                    .uri(webhookUri)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[LogFixer][WEBHOOK] incident 발송 완료. logHash={}, service={}", payload.getLogHash(), payload.getServiceName());
        } catch (Exception e) {
            // 실패해도 LC 흐름에 영향 없도록 warn만 기록
            log.warn("[LogFixer][WEBHOOK] 발송 실패 (무시). logHash={}, err={}", payload.getLogHash(), e.getMessage());
        }
    }
}