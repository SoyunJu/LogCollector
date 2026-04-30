package com.soyunju.logcollector.admin.repository;

import com.soyunju.logcollector.admin.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKey(String apiKey);
    List<ApiKey>     findAllByTenantId(String tenantId);
}