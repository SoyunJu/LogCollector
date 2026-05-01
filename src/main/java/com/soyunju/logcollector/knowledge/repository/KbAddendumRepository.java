package com.soyunju.logcollector.knowledge.repository;


import com.soyunju.logcollector.knowledge.domain.KbAddendum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbAddendumRepository extends JpaRepository<KbAddendum, Long> {

    List<KbAddendum> findByKbArticle_IdOrderByCreatedAtDesc(Long kbArticleId);

    List<KbAddendum> findByKbArticle_IdOrderByCreatedAtDesc(Long kbArticleId, Pageable pageable);

    boolean existsByKbArticle_Id(Long kbArticleId);

    List<KbAddendum> findTop3ByKbArticle_IdOrderByCreatedAtDesc(Long kbArticleId);

    long countByKbArticle_Id(Long kbArticleId);
}
