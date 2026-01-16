package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByLogHash(String logHash);

    // N+1 방지: 한번에 로딩
    List<Incident> findAllByLogHashIn(Collection<String> logHashes);
}
