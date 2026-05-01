package com.soyunju.logcollector.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lc.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private Login login = new Login();
    private Plan free    = new Plan();
    private Plan pro     = new Plan();
    private Plan enterprise = new Plan();

    @Getter
    @Setter
    public static class Login {
        private int maxAttempts = 5;
        private int banMinutes  = 15;
    }

    @Getter
    @Setter
    public static class Plan {
        private int requestsPerSecond = 10;
    }
}