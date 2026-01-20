package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.IncidentRankResponse;
import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.crd.IncidentService;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    // 목록 조회
    @GetMapping
    public ResponseEntity<Page<IncidentResponse>> list(
            @RequestParam(required = false) Boolean excludeResolved,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) ErrorLevel errorLevel,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Boolean serviceNameExact,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @PageableDefault(size = 20, sort = "lastOccurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                incidentSearchService.findIncidents(
                        excludeResolved,
                        status,
                        errorLevel,
                        serviceName,
                        serviceNameExact,
                        from,
                        to,
                        pageable
                )
        );
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
