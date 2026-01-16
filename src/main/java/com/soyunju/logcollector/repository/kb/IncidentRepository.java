package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByLogHash(String logHash);
}
