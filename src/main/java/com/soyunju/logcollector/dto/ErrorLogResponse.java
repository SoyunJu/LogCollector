package com.soyunju.logcollector.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorLogResponse {
    private Long logId;         // 생성된 로그 ID
    private String errorCode;   // 에러 코드 (예: ERR-500)
    private String summary;     // 메시지 요약
    private String serviceName; // 서비스명
    private String hostInfo;    // HostName(PodName)
    // 추가: 영향 서버 수(해당 incident(log_hash)가 발생한 host의 개수)
    private Long impactedHostCount;
}