package com.soyunju.logcollector.dto.lc;

import com.soyunju.logcollector.domain.lc.ErrorStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorLogResponse {
    private Long logId;         // 생성된 로그 ID
    private String errorCode;   // 에러 코드 (예: ERR-500)
    private String summary;     // 메시지 요약
    private String message;     // 원문 메시지
    private String stackTrace;  // 스택트레이스
    private java.time.LocalDateTime occurredTime;
    private java.time.LocalDateTime firstOccurredTime;
    private java.time.LocalDateTime lastOccurredTime;
    private String serviceName; // 서비스명
    private String hostInfo;    // HostName(PodName)
    private Long impactedHostCount; // 추가: 영향 서버 수(해당 incident(log_hash)가 발생한 host의 개수)
    private long repeatCount;
    private boolean isNew;
    private boolean isNewHost; // 서버 확산 여부
    private String logHash;          // 해시값 추가 테스트시 확인 용이할 용도
    private ErrorStatus status;
    private String logLevel;

}