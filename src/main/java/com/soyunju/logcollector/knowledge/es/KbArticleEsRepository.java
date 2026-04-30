package com.soyunju.logcollector.es;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface KbArticleEsRepository extends ElasticsearchRepository<KbArticleDocument, String> {

    Page<KbArticleDocument> findByIncidentTitleContainingOrContentContainingOrAddendumContentContaining(
            String title, String content, String addendum, Pageable pageable
    );
}