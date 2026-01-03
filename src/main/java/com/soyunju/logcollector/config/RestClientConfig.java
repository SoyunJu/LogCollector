package com.soyunju.logcollector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        // 스프링이 제공하는 builder를 주입받아 RestClient를 생성합니다.
        return builder.build();
    }
}