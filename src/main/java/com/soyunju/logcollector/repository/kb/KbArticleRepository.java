package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    Optional<KbArticle> findTopByIncident_LogHashOrderByCreatedAtDesc(String logHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(transactionManager = "kbTransactionManager")
    @Query("update KbArticle k set k.status = :toStatus where k.status in :fromStatuses and k.lastActivityAt < :cutoff")
    int bulkSetStatusByLastActivity(@Param("toStatus") KbStatus toStatus,
                                    @Param("fromStatuses") List<KbStatus> fromStatuses,
                                    @Param("cutoff") LocalDateTime cutoff);

    boolean existsByIncidentIdAndStatusIn(Long incidentId, Collection<KbStatus> statuses);

    @Query("select k from KbArticle k join fetch k.incident where k.id = :id")
    Optional<KbArticle> findByIdWithIncident(@Param("id") Long id);

    // 7일 지나도록 초안상태인 Kbarticle 은 del 조치
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(transactionManager = "kbTransactionManager")
    @Query("""
            delete from KbArticle k
            where k.createdBy = :createdBy
              and k.status = :status
              and k.createdAt < :createdBefore
              and k.lastActivityAt < :lastActivityBefore
            """)
    int deleteExpiredSystemDrafts(@Param("createdBy") com.soyunju.logcollector.domain.kb.enums.CreatedBy createdBy,
                                  @Param("status") com.soyunju.logcollector.domain.kb.enums.KbStatus status,
                                  @Param("createdBefore") java.time.LocalDateTime createdBefore,
                                  @Param("lastActivityBefore") java.time.LocalDateTime lastActivityBefore);

    Optional<KbArticle> findByIncident_Id(Long incidentId);

    // DEFINITE status
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(transactionManager = "kbTransactionManager")
    @Query("""
            update KbArticle k
            set k.status = :toStatus, k.confidenceLevel = :confidenceLevel
            where k.status in :fromStatuses and k.lastActivityAt < :cutoff
            """)
    int bulkPromoteDefiniteByLastActivity(@Param("toStatus") KbStatus toStatus,
                                          @Param("confidenceLevel") int confidenceLevel,
                                          @Param("fromStatuses") List<KbStatus> fromStatuses,
                                          @Param("cutoff") LocalDateTime cutoff);
}




