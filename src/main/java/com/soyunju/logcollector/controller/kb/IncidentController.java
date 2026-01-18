package com.soyunju.logcollector.controller.kb;

import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.service.kb.crd.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    // logHash로 단건 조회
    @GetMapping("/{logHash}")
    public ResponseEntity<IncidentResponse> getByLogHash(@PathVariable String logHash) {
        return incidentService.findByLogHash(logHash)
                .map(IncidentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
