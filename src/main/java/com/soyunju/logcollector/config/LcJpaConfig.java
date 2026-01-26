package com.soyunju.logcollector.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                "com.soyunju.logcollector.repository.lc",
                "com.soyunju.logcollector.repository.audit"
        },
        entityManagerFactoryRef = "lcEntityManagerFactory",
        transactionManagerRef = "lcTransactionManager"
)
@EntityScan(basePackages = {
        "com.soyunju.logcollector.domain.lc",
        "com.soyunju.logcollector.domain.audit"
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
    public LocalContainerEntityManagerFactoryBean lcEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("lcDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(
                        "com.soyunju.logcollector.domain.lc",
                        "com.soyunju.logcollector.domain.audit"
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
