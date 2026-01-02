package com.soyunju.logcollector.controller;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.service.ErrorLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    @PostMapping
    public ResponseEntity<ErrorLogResponse> collectLog(@Valid @RequestBody ErrorLogRequest request) {
        log.info("Receiving log from service: {}", request.getServiceName());
        ErrorLogResponse response = errorLogService.saveLog(request);
        if (response == null) {
            return ResponseEntity.noContent().build(); // 필터링된 경우 204 반환
        }

        return ResponseEntity.ok(response);
    }

    // 에러 로그 목록 조회 API
    @GetMapping
    public ResponseEntity<Page<ErrorLogResponse>> getLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) ErrorStatus status, // 추가된 파라미터
            @RequestParam(defaultValue = "false") boolean isToday,
            @PageableDefault(size = 20, sort = "occurrenceTime", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(errorLogService.findLogs(serviceName, status, isToday, pageable));
    }
}