package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    Optional<KbArticle> findTopByIncident_IdAndCreatedByAndStatusInOrderByCreatedAtDesc(
            Long incidentId,
            CreatedBy createdBy,
            List<KbStatus> status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update KbArticle k set k.status = :toStatus where k.status in :fromStatuses and k.lastActivityAt < :cutoff")
    int bulkSetStatusByLastActivity(@Param("toStatus") KbStatus toStatus,
                                    @Param("fromStatuses") List<KbStatus> fromStatuses,
                                    @Param("cutoff") LocalDateTime cutoff);

    boolean existsByIncidentIdAndStatusIn(Long incidentId, Collection<KbStatus> statuses);
}




