package com.soyunju.logcollector.repository.kb;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.dto.kb.IncidentSearch;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.soyunju.logcollector.domain.kb.QIncident.incident;

public class IncidentRepositoryImpl implements IncidentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public IncidentRepositoryImpl(@Qualifier("kbQueryFactory") JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Incident> search(IncidentSearch condition, Pageable pageable) {
        List<Incident> content = queryFactory
                .selectFrom(incident)
                .where(
                        serviceNameEq(condition.getServiceName()),
                        statusEq(condition.getStatus()),
                        levelEq(condition.getLevel()),
                        betweenDate(condition.getStartDate(), condition.getEndDate()),
                        keywordContains(condition.getKeyword())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(incident.lastOccurredAt.desc()) // 최근 발생 순 정렬
                .fetch();

        Long total = queryFactory
                .select(incident.count())
                .from(incident)
                .where(
                        serviceNameEq(condition.getServiceName()),
                        statusEq(condition.getStatus()),
                        levelEq(condition.getLevel()),
                        betweenDate(condition.getStartDate(), condition.getEndDate()),
                        keywordContains(condition.getKeyword())
                )
                .fetchOne();

        long totalCount = (total == null) ? 0L : total;

        return new PageImpl<>(content, pageable, totalCount);
    }

    private BooleanExpression serviceNameEq(String serviceName) {
        return StringUtils.hasText(serviceName) ? incident.serviceName.containsIgnoreCase(serviceName.trim()) : null;
    }

    private BooleanExpression statusEq(com.soyunju.logcollector.domain.kb.enums.IncidentStatus status) {
        return status != null ? incident.status.eq(status) : null;
    }

    private BooleanExpression levelEq(com.soyunju.logcollector.domain.kb.enums.ErrorLevel level) {
        return level != null ? incident.errorLevel.eq(level) : null;
    }

    private BooleanExpression betweenDate(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) return null;
        return incident.lastOccurredAt.between(start, end);
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword) ?
                incident.incidentTitle.containsIgnoreCase(keyword)
                        .or(incident.summary.containsIgnoreCase(keyword))
                        .or(incident.serviceName.containsIgnoreCase(keyword)) : null;
    }
}