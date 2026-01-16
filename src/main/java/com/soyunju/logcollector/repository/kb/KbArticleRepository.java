package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    Optional<KbArticle> findTopByIncident_IdOrderByCreatedAtDesc(Long incidentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update KbArticle k set k.status = :toStatus where k.status in :fromStatuses and k.lastActivityAt < :cutoff")
    int bulkSetStatusByLastActivity(@Param("toStatus") KbStatus toStatus,
                                    @Param("fromStatuses") List<KbStatus> fromStatuses,
                                    @Param("cutoff") LocalDateTime cutoff);
}
