package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/incidents")
public class IncidentSearchController {
    private final IncidentSearchService incidentSearchService;

    @GetMapping("/search")
    public ResponseEntity<Page<IncidentResponse>> search(
            com.soyunju.logcollector.dto.kb.IncidentSearch search,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentSearchService.searchIncidents(search, pageable));
    }
}
