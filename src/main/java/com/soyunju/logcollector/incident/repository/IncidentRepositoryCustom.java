package com.soyunju.logcollector.incident.repository;


import com.soyunju.logcollector.incident.domain.Incident;
import com.soyunju.logcollector.incident.dto.IncidentSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IncidentRepositoryCustom {
    // 검색 조건으로 목록 페이징
    Page<Incident> search(IncidentSearch condition, Pageable pageable);
}