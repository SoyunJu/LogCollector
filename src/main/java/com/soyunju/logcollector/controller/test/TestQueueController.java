package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.service.lc.redis.RedisToDB;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/test")
public class TestQueueController {

    private final RedisToDB redisToDB;

    /**
     * 테스트용: Redis 큐를 즉시 처리해서 LC/KB 반영을 강제함.
     * (IntelliJ HTTP Client에서 sleep/폴링 스크립트 없이도 검증 가능)
     */
    @PostMapping("/queue/drain")
    public ResponseEntity<String> drainQueueOnce() {
        redisToDB.pollAndProcess();
        return ResponseEntity.ok("Queue drained once.");
    }
}
