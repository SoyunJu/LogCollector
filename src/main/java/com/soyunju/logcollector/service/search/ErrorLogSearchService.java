package com.soyunju.logcollector.service.search;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.domain.QErrorLog;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.service.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ErrorLogSearchService {

    private final JPAQueryFactory queryFactory;
    private final ErrorLogRepository errorLogRepository;
    private final LogProcessor logProcessor;

    public Page<ErrorLogResponse> findLogs(String serviceName, ErrorStatus status, boolean isToday, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;
        BooleanBuilder builder = new BooleanBuilder();

        if (status != null) builder.and(errorLog.status.eq(status));
        if (serviceName != null && !serviceName.isEmpty()) builder.and(errorLog.serviceName.eq(serviceName));
        if (isToday) {
            LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            builder.and(errorLog.occurrenceTime.after(startOfToday));
        }

        List<ErrorLog> results = queryFactory.selectFrom(errorLog)
                .where(builder)
                .offset(pageable.getOffset()).limit(pageable.getPageSize())
                .orderBy(errorLog.occurrenceTime.desc()).fetch();

        long total = queryFactory.selectFrom(errorLog).where(builder).fetchCount();
        return new PageImpl<>(results.stream().map(logProcessor::convertToResponse).collect(Collectors.toList()), pageable, total);
    }

    public Page<ErrorLogResponse> getLogsByStatus(ErrorStatus status, Pageable pageable) {
        return errorLogRepository.findByStatus(status, pageable).map(logProcessor::convertToResponse);
    }

    public Page<ErrorLogResponse> getSortedLogs(String direction, Pageable pageable) {
        QErrorLog log = QErrorLog.errorLog;
        OrderSpecifier<?> timeOrder = direction.equalsIgnoreCase("desc") ? log.occurrenceTime.desc() : log.occurrenceTime.asc();

        List<ErrorLog> results = queryFactory.selectFrom(log)
                .orderBy(timeOrder, log.serviceName.desc())
                .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        long total = queryFactory.selectFrom(log).fetchCount();
        return new PageImpl<>(results.stream().map(logProcessor::convertToResponse).collect(Collectors.toList()), pageable, total);
    }
}
