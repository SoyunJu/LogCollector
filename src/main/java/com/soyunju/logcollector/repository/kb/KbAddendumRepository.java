package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbAddendum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbAddendumRepository extends JpaRepository<KbAddendum, Long> {

    List<KbAddendum> findByKbArticle_IdOrderByCreatedAtDesc(Long kbArticleId);

    List<KbAddendum> findByKbArticle_IdOrderByCreatedAtDesc(Long kbArticleId, Pageable pageable);

    boolean existsByKbArticle_Id(Long kbArticleId);

}
