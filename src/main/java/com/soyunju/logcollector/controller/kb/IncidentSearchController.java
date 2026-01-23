package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.IncidentRankResponse;
import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

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

    @GetMapping("/top")
    public ResponseEntity<List<IncidentRankResponse>> top(
            @RequestParam(defaultValue = "repeatCount") String metric,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to
    ) {
        return ResponseEntity.ok(
                incidentSearchService.top(metric, limit, serviceName, status, from, to)
        );

    }
}
