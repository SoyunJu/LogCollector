package com.soyunju.logcollector.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_key",   columnList = "apiKey"),
        @Index(name = "idx_tenant_id", columnList = "tenantId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String apiKey;

    @Column(nullable = false, length = 16)
    private String apiKeyHint;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String appId;

    // 식별 이름
    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String plan; // FREE / PRO / ENTERPRISE

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;   // null = 무기한

    @Column(nullable = false)
    private boolean revoked = false;

    private LocalDateTime revokedAt;

    @Builder
    public ApiKey(String apiKey, String apiKeyHint, String tenantId, String appId,
                  String name, String plan, LocalDateTime expiresAt) {
        this.apiKey      = apiKey;
        this.apiKeyHint  = apiKeyHint;
        this.tenantId    = tenantId;
        this.appId       = appId;
        this.name        = name;
        this.plan        = plan;
        this.createdAt   = LocalDateTime.now();
        this.expiresAt   = expiresAt;
    }

    public void revoke() {
        this.revoked   = true;
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}