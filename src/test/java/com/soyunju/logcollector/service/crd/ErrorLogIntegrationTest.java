package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ErrorLogIntegrationTest {

    @Container
    static final MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", mariadb::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("[TEST START] " + LocalDateTime.now());
        System.out.println("-".repeat(60));
    }

    @AfterEach
    void tearDown() {
        System.out.println("-".repeat(60));
        System.out.println("[TEST END] " + LocalDateTime.now());
        System.out.println("=".repeat(60) + "\n");
    }

    @Test
    void contextLoads() {
        assertThat(errorLogCrdService).isNotNull();
        assertThat(errorLogRepository).isNotNull();
    }
}
