package com.soyunju.logcollector.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway lcFlyway(@Qualifier("lcDataSource") DataSource lcDataSource) {
        return Flyway.configure()
                .dataSource(lcDataSource)
                .locations("classpath:db/migration/lc")
                .table("flyway_schema_history_lc")
                .baselineOnMigrate(true)   // 기존 테이블 있어도 시작
                .baselineVersion("0")
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway kbFlyway(@Qualifier("kbDataSource") DataSource kbDataSource) {
        return Flyway.configure()
                .dataSource(kbDataSource)
                .locations("classpath:db/migration/kb")
                .table("flyway_schema_history_kb")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}