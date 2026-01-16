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

        // 테스트에서는 스키마는 이미 수동 생성해둔 상태라면 validate가 안전
        // 만약 테스트가 스키마를 생성하도록 하고 싶으면 create-drop로 바꿔도 됨
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "false");
    }
}