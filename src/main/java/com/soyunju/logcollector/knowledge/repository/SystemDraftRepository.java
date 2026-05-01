package com.soyunju.logcollector.knowledge.repository;


import com.soyunju.logcollector.knowledge.domain.SystemDraft;
import com.soyunju.logcollector.knowledge.domain.enums.DraftReason;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SystemDraftRepository extends JpaRepository<SystemDraft, Long> {
    boolean existsByIncident_IdAndReason(Long incidentId, DraftReason reason);
}
