package com.soyunju.logcollector.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lc.webhook")
@Getter
@Setter
public class WebhookProperties {
    private String logfixerSecret;
}