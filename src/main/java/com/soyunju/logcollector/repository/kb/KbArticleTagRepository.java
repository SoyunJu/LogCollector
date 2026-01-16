package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbArticleTag;
import com.soyunju.logcollector.domain.kb.KbArticleTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbArticleTagRepository extends JpaRepository<KbArticleTag, KbArticleTagId> {
    List<KbArticleTag> findByKbArticleId(Long kbArticleId);
    void deleteByKbArticleId(Long kbArticleId);
}
