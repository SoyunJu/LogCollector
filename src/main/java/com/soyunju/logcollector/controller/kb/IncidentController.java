package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.IncidentRankResponse;
import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.crd.IncidentService;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentSearchService incidentSearchService;

    // logHash로 단건 조회
    @GetMapping("/{logHash}")
    public ResponseEntity<IncidentResponse> getByLogHash(@PathVariable String logHash) {
        return incidentService.findByLogHash(logHash)
                .map(IncidentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // [추가] 인시던트 전체 목록 조회 (index.html loadIncidents 대응)
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse>> getIncidents(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentService.findAll(pageable));
    }

    // 랭킹 조회
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
