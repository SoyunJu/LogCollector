package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.SystemDraft;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDraftRepository extends JpaRepository<SystemDraft, Long> {
    boolean existsByIncident_IdAndReason(Long incidentId, DraftReason reason);
}
