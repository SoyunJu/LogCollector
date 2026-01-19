package com.soyunju.logcollector.kb;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class MariaDbContainerTestBase {

    static final MariaDBContainer<?> maria =
            new MariaDBContainer<>("mariadb:11.4")
                    .withDatabaseName("logcollector")
                    .withUsername("test")
                    .withPassword("test");

    static {
        maria.start();
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", maria::getJdbcUrl);
        registry.add("spring.datasource.username", maria::getUsername);
        registry.add("spring.datasource.password", maria::getPassword);

        // 수정: validate 대신 update를 사용하여 테스트 시 테이블 자동 생성
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "true"); // 쿼리 확인을 위해 true 권장
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    }
}