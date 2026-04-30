package com.soyunju.logcollector.knowledge.service.webhook;

import com.soyunju.logcollector.global.config.WebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;


@Component
@RequiredArgsConstructor
public class WebhookSigner {

    private static final String ALGORITHM    = "HmacSHA256";
    private static final String HEADER_TS    = "X-LC-Timestamp";
    private static final String HEADER_SIG   = "X-LC-Signature";

    private final WebhookProperties webhookProperties;


    public java.util.Map<String, String> generateHeaders(String requestBody) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(timestamp, requestBody);

        return java.util.Map.of(
                HEADER_TS,  timestamp,
                HEADER_SIG, signature
        );
    }


    // 서명 생성
    private String sign(String timestamp, String requestBody) {
        try {
            String message   = timestamp + "." + requestBody;
            byte[] keyBytes  = webhookProperties.getLogfixerSecret()
                    .getBytes(StandardCharsets.UTF_8);
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);

            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);

            byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);

        } catch (Exception e) {
            throw new IllegalStateException("웹훅 서명 생성 실패", e);
        }
    }
}