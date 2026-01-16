package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@SpringBootTest
@Transactional
class ErrorLogIntegrationTest {

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 [TEST START] " + LocalDateTime.now());
        System.out.println("-".repeat(60));
    }

    @AfterEach
    void tearDown() {
        System.out.println("-".repeat(60));
        System.out.println("🏁 [TEST END] " + LocalDateTime.now());
        System.out.println("=".repeat(60) + "\n");
    }

}