package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/test")
public class TestHashController {

    private final LogProcessor logProcessor;

    /**
     * 테스트용: serviceName + message(+stackTrace optional)로
     * 실제 수집 로직과 동일한 방식으로 logHash(incident hash) 계산.
     *
     * body 예시:
     * {
     *   "serviceName": "TESTE-API",
     *   "message": "[TEST_HTTP] ...",
     *   "stackTrace": ""   // optional
     * }
     */
    @PostMapping("/hash")
    public ResponseEntity<Map<String, String>> calcHash(@RequestBody Map<String, String> body) {
        String serviceName = body.get("serviceName");
        String message = body.get("message");
        String stackTrace = body.getOrDefault("stackTrace", "");

        if (serviceName == null || serviceName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "serviceName is required"));
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        String logHash = logProcessor.generateIncidentHash(serviceName, message, stackTrace);
        return ResponseEntity.ok(Map.of("logHash", logHash));
    }
}
