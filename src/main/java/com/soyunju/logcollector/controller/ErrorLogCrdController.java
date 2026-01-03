package com.soyunju.logcollector.controller;

import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.service.crd.ErrorLogCrdService;
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

    // 로그 수집 API (Create)
    @PostMapping
    public ResponseEntity<ErrorLogResponse> collectLog(@Valid @RequestBody ErrorLogRequest request) {
        log.info("로그 수집 요청 - 서비스: {}", request.getServiceName());
        // 중복일 경우 기존 로그의 시간을 업데이트하고 해당 정보를 반환합니다.
        ErrorLogResponse response = errorLogCrdService.saveLog(request);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
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