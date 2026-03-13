package com.soyunju.logcollector.controller.lc;

import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorResponse;
import com.soyunju.logcollector.exception.ErrorCode;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import com.soyunju.logcollector.service.lc.redis.LogToRedis;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [Flow: Log Ingest]
 * 운영 시스템/에이전트/프론트가 에러 로그를 전송하면,
 * 1) 최소 검증/정규화 후
 * 2) Redis 큐에 적재
 * 3) (별도 consumer/processor가) Incident로 승격/병합
 */
@Tag(name = "01. Log Ingest", description = "로그 수집(ingest) → Redis 큐 적재(Incident 승격은 별도 처리)")
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ErrorLogCrdController {

    private final ErrorLogCrdService errorLogCrdService;
    private final LogToRedis logToRedis;
    private final LogProcessor logProcessor;

    // 로그 수집 API (Create)
    @Operation(
            summary = "POST /api/logs - 로그 수집(큐 적재)",
            description = "유효한 에러 로그만 Redis 큐에 push합니다. (Incident 생성/병합은 비동기 처리)"
    )
    @PostMapping
    public ResponseEntity<?> collectLog(@Valid @RequestBody ErrorLogRequest request) {
        log.info("로그 수집 요청 - 서비스: {}", request.getServiceName());

        //  로그 레벨 (입력값 우선, 없으면 메시지에서 추론)
        String level = StringUtils.hasText(request.getLogLevel())
                ? request.getLogLevel()
                : logProcessor.inferLogLevel(request.getMessage());

        if (!logProcessor.isTargetLevel(level)) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(ErrorCode.INVALID_LOG_LEVEL, level));
        }
        request.setLogLevel(level.toUpperCase());

        // 검증된 로그만 Redis 큐에 push
        logToRedis.push(request);

        // 202 Accepted: 비동기 처리(incident 승격/병합은 consumer에서 진행)
        return ResponseEntity.accepted().build();
    }

    // 상태 업데이트 API (Update)
   /* @PatchMapping("/{logId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long logId,
            @RequestParam ErrorStatus newStatus) {
        errorLogCrdService.updateStatus(logId, newStatus);
        return ResponseEntity.ok().build();
    } */

    // 다중 로그 삭제 API (Delete)
    @Hidden
    @DeleteMapping
    public ResponseEntity<Void> deleteLogs(@RequestBody List<Long> logIds) {
        errorLogCrdService.deleteLogs(logIds);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @PatchMapping("/{logId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long logId,
            @RequestParam ErrorStatus newStatus) {
        errorLogCrdService.updateStatus(logId, newStatus);
        return ResponseEntity.ok().build();
    }

}
