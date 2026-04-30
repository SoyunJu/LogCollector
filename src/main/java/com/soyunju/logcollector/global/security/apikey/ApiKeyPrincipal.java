package com.soyunju.logcollector.global.security.apikey;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ApiKeyPrincipal {

    private final Long   apiKeyId;
    private final String tenantId;
    private final String appId;
    private final String plan;
}