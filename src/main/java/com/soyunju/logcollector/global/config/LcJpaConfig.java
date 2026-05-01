package com.soyunju.logcollector.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(
        basePackages = {
                "com.soyunju.logcollector.collector.repository",
                "com.soyunju.logcollector.admin.repository",
                "com.soyunju.logcollector.global.audit.repository"
        },
        entityManagerFactoryRef = "lcEntityManagerFactory",
        transactionManagerRef = "lcTransactionManager"
)
@EntityScan(basePackages = {
        "com.soyunju.logcollector.collector.domain",
        "com.soyunju.logcollector.admin.domain",
        "com.soyunju.logcollector.global.audit.domain"
})
public class LcJpaConfig {

    @Primary
    @Bean(name = "lcDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.lc")
    public DataSource lcDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "lcEntityManagerFactory")
    @DependsOn("lcFlyway")
    public LocalContainerEntityManagerFactoryBean lcEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("lcDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(
                        "com.soyunju.logcollector.collector.domain",
                        "com.soyunju.logcollector.admin.domain",
                        "com.soyunju.logcollector.global.audit.domain"
                )
                .persistenceUnit("lc")
                .build();
    }

    @Primary
    @Bean(name = "lcTransactionManager")
    public PlatformTransactionManager lcTransactionManager(
            @Qualifier("lcEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}