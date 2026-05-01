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
                "com.soyunju.logcollector.knowledge.repository",
                "com.soyunju.logcollector.incident.repository"
        },
        entityManagerFactoryRef = "kbEntityManagerFactory",
        transactionManagerRef = "kbTransactionManager"
)
@EntityScan(basePackages = {
        "com.soyunju.logcollector.knowledge.domain",
        "com.soyunju.logcollector.incident.domain"
})
public class KbJpaConfig {

    @Bean(name = "kbDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.kb")
    public DataSource kbDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "kbEntityManagerFactory")
    @DependsOn("kbFlyway")
    public LocalContainerEntityManagerFactoryBean kbEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("kbDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(
                        "com.soyunju.logcollector.knowledge.domain",
                        "com.soyunju.logcollector.incident.domain"
                )
                .persistenceUnit("kb")
                .build();
    }

    @Bean(name = "kbTransactionManager")
    public PlatformTransactionManager kbTransactionManager(
            @Qualifier("kbEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}