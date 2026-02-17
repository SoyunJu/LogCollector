package com.soyunju.logcollector.controller.lc;

import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.service.lc.search.ErrorLogSearchService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. Log Ingest", description = "로그 수집(ingest) → Redis 큐 적재(Incident 승격은 별도 처리)")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ErrorLogSearchController {

    private final ErrorLogSearchService errorLogSearchService;

    @Operation(
            summary = "GET /api/logs - log조회",
            description = "수집된 error log list 를 조회합니다."
    )
    @GetMapping
    public ResponseEntity<Page<ErrorLogResponse>> getLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ErrorStatus status,
            @RequestParam(defaultValue = "false") boolean isToday,
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "lastOccurredTime",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.findLogs(serviceName, keyword, status, isToday, pageable));
    }

    // 상태별 필터링 조회 API
    @Hidden
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ErrorLogResponse>> getLogsByStatus(
            @PathVariable ErrorStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.getLogsByStatus(status, pageable));
    }

    // 특수 정렬 조회 API (Latest/Oldest + Service Name DESC)
    @Hidden
    @GetMapping("/sorted")
    public ResponseEntity<Page<ErrorLogResponse>> getSortedLogs(
            @RequestParam(defaultValue = "desc") String direction,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogSearchService.getSortedLogs(direction, pageable));
    }
}