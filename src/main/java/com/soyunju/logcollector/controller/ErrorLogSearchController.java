package com.soyunju.logcollector.controller;

import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.service.search.ErrorLogSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ErrorLogSearchController {

    private final ErrorLogSearchService errorLogSearchService;

    // 동적 조건 검색 API
    @GetMapping
    public ResponseEntity<Page<ErrorLogResponse>> getLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) ErrorStatus status,
            @RequestParam(defaultValue = "false") boolean isToday,
            @PageableDefault(size = 20, sort = "occurrenceTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.findLogs(serviceName, status, isToday, pageable));
    }

    // 상태별 필터링 조회 API
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ErrorLogResponse>> getLogsByStatus(
            @PathVariable ErrorStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.getLogsByStatus(status, pageable));
    }

    // 특수 정렬 조회 API (Latest/Oldest + Service Name DESC)
    @GetMapping("/sorted")
    public ResponseEntity<Page<ErrorLogResponse>> getSortedLogs(
            @RequestParam(defaultValue = "desc") String direction,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.getSortedLogs(direction, pageable));
    }
}