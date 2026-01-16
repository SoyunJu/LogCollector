package com.soyunju.logcollector.service.lc.search;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.domain.lc.QErrorLog;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ErrorLogSearchService {

    private final JPAQueryFactory queryFactory;
    private final ErrorLogRepository errorLogRepository;
    private final LogProcessor logProcessor;

    private static final QErrorLog errorLog = QErrorLog.errorLog;

    public Page<ErrorLogResponse> findLogs(
            String serviceName,
            ErrorStatus status,
            boolean isToday,
            Pageable pageable
    ) {
        BooleanBuilder builder = buildCommonCondition(serviceName, status, isToday);
        return fetchPage(builder, pageable, errorLog.occurredTime.desc());
    }

    public Page<ErrorLogResponse> getLogsByStatus(ErrorStatus status, Pageable pageable) {
        return errorLogRepository
                .findByStatus(status, pageable)
                .map(logProcessor::convertToResponse);
    }

    public Page<ErrorLogResponse> getSortedLogs(String direction, Pageable pageable) {
        OrderSpecifier<?> timeOrder =
                "desc".equalsIgnoreCase(direction)
                        ? errorLog.occurredTime.desc()
                        : errorLog.occurredTime.asc();

        return fetchPage(null, pageable, timeOrder, errorLog.serviceName.desc());
    }

    /* =========================
       공통 로직
       ========================= */

    private BooleanBuilder buildCommonCondition(
            String serviceName,
            ErrorStatus status,
            boolean isToday
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        if (status != null) {
            builder.and(errorLog.status.eq(status));
        }

        if (serviceName != null && !serviceName.isEmpty()) {
            builder.and(errorLog.serviceName.eq(serviceName));
        }

        if (isToday) {
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            builder.and(errorLog.occurredTime.after(startOfToday));
        }

        return builder;
    }

    private Page<ErrorLogResponse> fetchPage(
            BooleanBuilder condition,
            Pageable pageable,
            OrderSpecifier<?>... orderSpecifiers
    ) {
        List<ErrorLog> results = queryFactory
                .selectFrom(errorLog)
                .where(condition)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory
                .selectFrom(errorLog)
                .where(condition)
                .fetchCount();

        return new PageImpl<>(
                results.stream()
                        .map(logProcessor::convertToResponse)
                        .toList(),
                pageable,
                total
        );
    }
}
