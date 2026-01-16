package com.soyunju.logcollector.controller.lc;

import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.lc.redis.LogToRedis;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ErrorLogCrdController {

    private final ErrorLogCrdService errorLogCrdService;
    private final LogToRedis logToRedis;

    // 로그 수집 API (Create)
    @PostMapping
    public ResponseEntity<Void> collectLog(@Valid @RequestBody ErrorLogRequest request) {
        log.info("로그 수집 요청 - 서비스: {}", request.getServiceName());
        // Redis 큐에 log push
        logToRedis.push(request);
        return ResponseEntity.accepted().build();
    }

    // 상태 업데이트 API (Update)
    @PatchMapping("/{logId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long logId,
            @RequestParam ErrorStatus newStatus) {
        errorLogCrdService.updateStatus(logId, newStatus);
        return ResponseEntity.ok().build();
    }

    // 다중 로그 삭제 API (Delete)
    @DeleteMapping
    public ResponseEntity<Void> deleteLogs(@RequestBody List<Long> logIds) {
        errorLogCrdService.deleteLogs(logIds);
        return ResponseEntity.noContent().build();
    }
}