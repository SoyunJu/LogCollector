package old;

import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @PageableDefault(size = 20, sort = "occurredTime", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(errorLogService.findLogs(serviceName, status, isToday, pageable));
    }

    // AI 분석을 위한 API
    @PostMapping("/{logId}/analyze")
    public ResponseEntity<AiAnalysisResult> startAiAnalysis(@PathVariable Long logId) {
        // 서비스로부터 분석 결과를 직접 받아 리턴합니다.
        AiAnalysisResult result = errorLogService.startAiAnalysis(logId);
        return ResponseEntity.ok(result);
    }

    // RUD
    @DeleteMapping
    public ResponseEntity<Void> deleteLogs(@RequestBody List<Long> logIds) {
        errorLogService.deleteLogs(logIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ErrorLogResponse>> getLogsByStatus(
            @PathVariable ErrorStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogService.getLogsByStatus(status, pageable));
    }

    @PatchMapping("/{logId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long logId,
            @RequestParam ErrorStatus newStatus) {
        errorLogService.updateStatus(logId, newStatus);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sorted")
    public ResponseEntity<Page<ErrorLogResponse>> getSortedLogs(
            @RequestParam(defaultValue = "desc") String direction,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(errorLogService.getSortedLogs(direction, pageable));
    }

}