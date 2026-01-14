package old;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.domain.QErrorLog;
import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.service.ai.OpenAiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final JPAQueryFactory queryFactory; // QuerydslConfig에서 설정한 Bean
    private final ErrorLogRepository errorLogRepository;
    private final OpenAiAnalysisService openAiAnalysisService;

    @Transactional
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        String level = dto.getLogLevel().toUpperCase();

        if (!isTargetLevel(level)) {
            log.info("Filtered out log level: {}", level);
            return null;
        }

        // 1. 저장 시점에 데이터 가공 (요청하신 로직 반영)
        String errorCode = generateErrorCode(dto.getMessage());
        String summary = extractSummary(dto.getStackTrace() != null ? dto.getStackTrace() : dto.getMessage());

        // 2. 가공된 데이터를 포함하여 엔티티 빌드 및 저장
        ErrorLog errorLog = ErrorLog.builder()
                .serviceName(dto.getServiceName())
                .hostName(dto.getHostName())
                .logLevel(level)
                .message(dto.getMessage())
                .stackTrace(dto.getStackTrace())
                .errorCode(errorCode) // 필드 매핑
                .summary(summary)     // 필드 매핑
                .build();

        ErrorLog saved = errorLogRepository.save(errorLog);
        log.error("New Critical Log Saved: ID={}, Code={}", saved.getId(), errorCode);

        return ErrorLogResponse.builder()
                .logId(saved.getId())
                .errorCode(saved.getErrorCode())
                .summary(saved.getSummary())
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
     * return errorLogRepository.findAllByOrderByoccurredTimeDesc();
     * }
     */

    // 로그 조회 로직 추가
    public Page<ErrorLogResponse> findLogs(String serviceName, ErrorStatus status, boolean isToday, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;
        BooleanBuilder builder = new BooleanBuilder();

        if (status != null) {
            builder.and(errorLog.status.eq(status));
        }
        if (serviceName != null && !serviceName.isEmpty()) {
            builder.and(errorLog.serviceName.eq(serviceName));
        }
        if (isToday) {
            LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            builder.and(errorLog.occurredTime.after(startOfToday));
        }

        // 2. Querydsl을 이용한 쿼리 실행
        List<ErrorLog> results = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.occurredTime.desc())
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
            builder.and(errorLog.occurredTime.after(startOfToday));
        }

        // 2. Querydsl을 이용한 쿼리 실행
        List<ErrorLog> results = queryFactory
                .selectFrom(errorLog)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.occurredTime.desc())
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

// oepnAi 호출 메서드 추가
  public AiAnalysisResult startAiAnalysis(Long logId) {
      if (!errorLogRepository.existsById(logId)) {
          throw new IllegalArgumentException("존재하지 않는 로그 ID입니다: " + logId);
      }

      // AI 서비스로부터 결과를 받아 그대로 반환
      return openAiAnalysisService.openAiAnalysis(logId);
  }

  // RUD
  @Transactional
  public void deleteLogs(List<Long> logIds) {
      if (logIds == null || logIds.isEmpty()) {
          throw new IllegalArgumentException("삭제할 로그 ID가 지정되지 않았습니다.");
      }
      errorLogRepository.deleteAllByIdInBatch(logIds);
  }

    @Transactional(readOnly = true)
    public Page<ErrorLogResponse> getLogsByStatus(ErrorStatus status, Pageable pageable) {
        return errorLogRepository.findByStatus(status, pageable)
                .map(this::convertToResponse);
    }

    @Transactional
    public void updateStatus(Long logId, ErrorStatus newStatus) {
        ErrorLog errorLog = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("해당 로그가 존재하지 않습니다. ID: " + logId));

        errorLog.setStatus(newStatus);

        // RESOLVED 상태로 변경될 경우 해결 시각 기록 (필요 시)
       /* if (newStatus == ErrorStatus.RESOLVED) {
            errorLog.setResolvedAt(LocalDateTime.now());
        } */
    }
    public Page<ErrorLogResponse> getSortedLogs(String direction, Pageable pageable) {
        QErrorLog log = QErrorLog.errorLog;

        // 정렬 순서 정의
        OrderSpecifier<?> timeOrder = direction.equalsIgnoreCase("desc") ?
                log.occurredTime.desc() : log.occurredTime.asc();

        List<ErrorLog> results = queryFactory
                .selectFrom(log)
                .orderBy(timeOrder, log.serviceName.desc()) // 요구사항: 동일 시각 시 서비스 네임 DESC
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory.selectFrom(log).fetchCount();

        return new PageImpl<>(results.stream().map(this::convertToResponse).collect(Collectors.toList()),
                pageable, total);
    }

}
