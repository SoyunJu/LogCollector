package com.soyunju.logcollector.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // 이 클래스가 스프링의 설정 파일임을 선언합니다.
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager; // JPA가 DB와 통신할 때 쓰는 핵심 객체입니다.

    @Bean // JPAQueryFactory를 스프링 빈으로 등록하여 어디서든 주입받을 수 있게 합니다.
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}