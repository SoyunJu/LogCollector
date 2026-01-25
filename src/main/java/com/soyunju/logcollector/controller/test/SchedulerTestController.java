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
        // kbScheduler.processLcIgnoreOutbox();
        kbScheduler.autoCloseResolvedIncidents();
        return ResponseEntity.ok("Scheduler triggered manually.");
    }

}