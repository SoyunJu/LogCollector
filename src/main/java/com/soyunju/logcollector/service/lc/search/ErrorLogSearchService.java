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


    private BooleanBuilder buildCommonCondition(
            String serviceName,
            ErrorStatus status,
            boolean isToday
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        // 상태 필터: null이 아닐 때만 조건 추가
        if (status != null) {
            builder.and(errorLog.status.eq(status));
        }

        // serviceName이 있을 때만 eq 조건 추가 (index.html의 filterService 대응)
        if (org.springframework.util.StringUtils.hasText(serviceName)) {
            builder.and(errorLog.serviceName.eq(serviceName.trim()));
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
        // QueryDSL의 where는 null이나 empty builder를 안전하게 처리하지만, 명시적으로 처리
        var query = queryFactory
                .selectFrom(errorLog)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        if (condition.hasValue()) { // 조건이 있을 때만 where 추가
            query.where(condition);
        }

        List<ErrorLog> results = query.fetch();

        var countQuery = queryFactory
                .select(errorLog.count())
                .from(errorLog);

        if (condition.hasValue()) {
            countQuery.where(condition);
        }

        Long total = countQuery.fetchOne();

        return new PageImpl<>(
                results.stream()
                        .map(logProcessor::convertToResponse)
                        .toList(),
                pageable,
                total != null ? total : 0L
        );
    }
}
