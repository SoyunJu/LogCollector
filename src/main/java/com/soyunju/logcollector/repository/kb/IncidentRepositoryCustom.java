package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.dto.kb.IncidentSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IncidentRepositoryCustom {
    // 검색 조건으로 목록 페이징
    Page<Incident> search(IncidentSearch condition, Pageable pageable);
}