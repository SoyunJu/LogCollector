package com.soyunju.logcollector.knowledge.repository;


import com.soyunju.logcollector.knowledge.domain.KbArticleTag;
import com.soyunju.logcollector.knowledge.domain.KbArticleTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbArticleTagRepository extends JpaRepository<KbArticleTag, KbArticleTagId> {
    List<KbArticleTag> findByKbArticleId(Long kbArticleId);
    void deleteByKbArticleId(Long kbArticleId);
}
