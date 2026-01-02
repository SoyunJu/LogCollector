package com.soyunju.logcollector.service;

import com.soyunju.logcollector.domain.AnalysisStatus;
import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.domain.QErrorLog;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.BooleanBuilder;


@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final JPAQueryFactory queryFactory; // QuerydslConfig에서 설정한 Bean
    private final ErrorLogRepository errorLogRepository;

    @Transactional
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        String level = dto.getLogLevel().toUpperCase();

        if (!isTargetLevel(level)) {
            log.info("Filtered out log level: {}", level); // SLF4J 사용
            return null;
        }

        String errorCode = generateErrorCode(dto.getMessage());

        // 30라인 요약 추출 로직 적용
        String summary = extractSummary(dto.getStackTrace() != null ? dto.getStackTrace() : dto.getMessage());

        ErrorLog errorLog = ErrorLog.builder().serviceName(dto.getServiceName()).hostName(dto.getHostName()).logLevel(level).message(dto.getMessage()).stackTrace(dto.getStackTrace()).build();

        ErrorLog saved = errorLogRepository.save(errorLog);
        log.error("New Critical Log Saved: ID={}, Code={}", saved.getId(), errorCode);

        // AI 연동 위해 추가
        analyzeErrorWithAI(saved);

        return ErrorLogResponse.builder()
                .logId(saved.getId())
                .summary(extractSummary(dto.getStackTrace())) //요약본
                .errorCode(errorCode)
                .summary(summary)
                .serviceName(saved.getServiceName())
                .hostInfo(saved.getHostName())
                .build();
    }

    private String extractSummary(String content) {
        if (content == null || content.isEmpty()) return "No content available";

        String[] lines = content.split("\\r?\\n");
        StringBuilder summaryBuilder = new StringBuilder();

        // 실제 'ERROR'나 'Exception'이 시작되는 지점 찾기
        int startLine = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("ERROR") || lines[i].contains("Exception") || lines[i].contains("FATAL")) {
                startLine = i;
                break;
            }
        }
        // 해당 지점부터 최대 30라인 추출
        for (int i = startLine; i < Math.min(startLine + 30, lines.length); i++) {
            summaryBuilder.append(lines[i]).append("\n");
        }
        return summaryBuilder.toString().trim();
    }

    private boolean isTargetLevel(String level) {
        return level.equals("ERROR") || level.equals("CRITICAL") || level.equals("FATAL");
    }

    private String generateErrorCode(String message) {
        if (message.contains("Database") || message.contains("SQL")) return "DB-ERR-001";
        if (message.contains("Timeout") || message.contains("Connection")) return "NET-ERR-001";
        if (message.contains("NullPointer")) return "SYS-ERR-001";
        return "GEN-ERR-999"; // 일반 에러
    }

    /**
     * 전체 에러 로그 조회
     * public List<ErrorLog> findAllLogs() {
     * return errorLogRepository.findAllByOrderByOccurrenceTimeDesc();
     * }
     */

    // 로그 조회 로직 추가
    public Page<ErrorLogResponse> findLogs(String serviceName, ErrorStatus status, boolean isToday, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;
        BooleanBuilder builder = new BooleanBuilder();

        // 1. 동적 조건 생성 (운영 환경의 다양한 요구사항 반영)
        if (status != null) {
            builder.and(errorLog.status.eq(status));
        }
        if (serviceName != null && !serviceName.isEmpty()) {
            builder.and(errorLog.serviceName.eq(serviceName));
        }
        if (isToday) {
            LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            builder.and(errorLog.occurrenceTime.after(startOfToday));
        }

        // 2. Querydsl을 이용한 쿼리 실행
        List<ErrorLog> results = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.occurrenceTime.desc())
                .fetch();

        // 3. 전체 카운트 조회 (페이징용)
        long total = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .fetchCount();

        // 4. DTO 변환 및 결과 반환
        List<ErrorLogResponse> dtoList = results.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, total);
    }

    /**
     * Entity를 Response DTO로 변환하는 공통 메서드
     */
    private ErrorLogResponse convertToResponse(ErrorLog log) {
        return ErrorLogResponse.builder()
                .logId(log.getId())
                .serviceName(log.getServiceName())
                .summary(extractSummary(log.getStackTrace())) // 앞서 만든 요약 로직 활용
                .errorCode(generateErrorCode(log.getMessage())) // 앞서 만든 에러코드 생성 로직 활용
                .hostInfo(log.getHostName())
                .build();
    }

  /**  public Page<ErrorLogResponse> findLogs(String serviceName, ErrorStatus status, boolean isToday, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;
        BooleanBuilder builder = new BooleanBuilder();

        // 1. 동적 조건 생성 (운영 환경의 다양한 요구사항 반영)
        if (status != null) {
            builder.and(errorLog.status.eq(status));
        }
        if (serviceName != null && !serviceName.isEmpty()) {
            builder.and(errorLog.serviceName.eq(serviceName));
        }
        if (isToday) {
            LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            builder.and(errorLog.occurrenceTime.after(startOfToday));
        }

        // 2. Querydsl을 이용한 쿼리 실행
        List<ErrorLog> results = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.occurrenceTime.desc())
                .fetch();

        // 3. 전체 카운트 조회 (페이징용)
        long total = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .fetchCount();

        // 4. DTO 변환 및 결과 반환
        List<ErrorLogResponse> dtoList = results.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, total);
    } **/

// ai 연동 메서드 추가
private void analyzeErrorWithAI(ErrorLog errorLog) {
    // 실제 AI API(OpenAI 등)로 교체될 부분입니다.
    if (errorLog.getMessage().contains("NullPointer")) {
        errorLog.setAiRootCause("객체 참조가 null인 상태에서 메서드 호출이 발생했습니다.");
        errorLog.setAiSuggestion("해당 변수의 초기화 여부와 Optional 처리를 확인하세요.");
        errorLog.setAnalysisStatus(AnalysisStatus.COMPLETED);
    }
}
}