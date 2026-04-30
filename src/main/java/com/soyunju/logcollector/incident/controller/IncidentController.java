package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.crud.IncidentBridgeService;
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import com.soyunju.logcollector.service.kb.search.IncidentSearchService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "02. Incident",
        description = "인시던트(Incident) 단위 상태변경/정리. (주 식별자: logHash)"
)
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentBridgeService incidentBridgeService;
    private final IncidentSearchService incidentSearchService;

    @Hidden
    @GetMapping("/closed")
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse>> getClosedIncidents(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentService.findClosed(pageable));
    }

    // logHash로 단건 조회
    @Operation(
            summary = "GET /api/incidents/{logHash} - Incident 단건 조회",
            description = "logHash 기준으로 incident 상세를 조회합니다."
    )
    @GetMapping("/{logHash}")
    public ResponseEntity<IncidentResponse> getByLogHash(@PathVariable String logHash) {
        return incidentService.findByLogHash(logHash)
                .map(IncidentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Hidden
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse>> getIncidents(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(incidentService.findAll(pageable));
    }

    @Operation(
            summary = "PATCH /api/incidents/{logHash}/status - Incident 상태 변경",
            description = "OPEN/IGNORED/RESOLVED 등 상태를 logHash 기준으로 변경합니다."
    )
    @PatchMapping("/{logHash}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String logHash,
            @RequestParam com.soyunju.logcollector.domain.kb.enums.IncidentStatus newStatus) {
        incidentBridgeService.updateStatus(logHash, newStatus);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "PATCH /api/incidents/{logHash}/details - Incident 메타 갱신",
            description = "title/createdBy/status 등 선택적 필드 갱신. null 파라미터는 변경하지 않습니다."
    )
    @PatchMapping("/{logHash}/details")
    public ResponseEntity<Void> updateDetails(
            @PathVariable String logHash,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) com.soyunju.logcollector.domain.kb.enums.IncidentStatus status) {
        incidentBridgeService.updateDetails(logHash, title, createdBy, status);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "POST /api/incidents/{logHash}/unignore - IGNORE 해제",
            description = "IGNORED 상태를 해제하고 다시 수집 가능한 상태로 되돌립니다."
    )
    @PostMapping("/{logHash}/unignore")
    public ResponseEntity<Void> unignore(@PathVariable String logHash) {
        incidentBridgeService.unignore(logHash);
        return ResponseEntity.ok().build();
    }
}
