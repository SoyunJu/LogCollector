package com.soyunju.logcollector.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

    @PersistenceContext(unitName = "lc")
    private EntityManager lcEntityManager;

    @PersistenceContext(unitName = "kb")
    private EntityManager kbEntityManager;

    @Bean(name = "lcQueryFactory")
    public JPAQueryFactory lcQueryFactory() {
        return new JPAQueryFactory(lcEntityManager);
    }

    @Bean(name = "kbQueryFactory")
    public JPAQueryFactory kbQueryFactory() {
        return new JPAQueryFactory(kbEntityManager);
    }
}
