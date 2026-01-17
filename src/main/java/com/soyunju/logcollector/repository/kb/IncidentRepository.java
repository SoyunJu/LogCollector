package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByLogHash(String logHash);

    // 추가: DraftPolicyService에서 대량 조회를 위해 필요함
    // N + 1 방지
    List<Incident> findAllByLogHashIn(java.util.Set<String> logHashes);
}
