package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentSearchService incidentSearchService;

    @GetMapping("/closed")
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse>> getClosedIncidents(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentService.findClosed(pageable));
    }

    // logHash로 단건 조회
    @GetMapping("/{logHash}")
    public ResponseEntity<IncidentResponse> getByLogHash(@PathVariable String logHash) {
        return incidentService.findByLogHash(logHash)
                .map(IncidentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse>> getIncidents(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentService.findAll(pageable));
    }

    // 랭킹 조회
 /*   @GetMapping("/top")
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
    } */

  /*  @PatchMapping("/{incidentId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long incidentId,
            @RequestParam IncidentStatus status) {
        incidentService.updateStatus(incidentId, status); // Service에 이 메서드가 구현되어 있어야 함
        return ResponseEntity.ok().build();
    } */

    @PatchMapping("/{logHash}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String logHash,
            @RequestParam com.soyunju.logcollector.domain.kb.enums.IncidentStatus newStatus) {
        incidentService.updateStatus(logHash, newStatus);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{logHash}/details")
    public ResponseEntity<Void> updateDetails(
            @PathVariable String logHash,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) com.soyunju.logcollector.domain.kb.enums.IncidentStatus status) {
        incidentService.updateDetails(logHash, title, createdBy, status);
        return ResponseEntity.ok().build();
    }

}
