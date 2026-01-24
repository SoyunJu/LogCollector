package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.service.scheduler.KbScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/scheduler")
@RequiredArgsConstructor
public class SchedulerTestController {

    private final KbScheduler kbScheduler;

    @PostMapping("/run")
    public ResponseEntity<String> runSchedulerManually() {
        // 제공해주신 스케줄러 메서드 호출
        kbScheduler.processLcIgnoreOutbox();
        // 필요하다면 kbScheduler.scheduleDraftCleanup(); 도 호출 가능
        return ResponseEntity.ok("Scheduler (processLcIgnoreOutbox) triggered manually.");
    }
}