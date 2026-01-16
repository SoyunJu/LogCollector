package com.soyunju.logcollector.dto.lc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private int status;      // HTTP 상태 코드
    private String code;     // 내부 에러 코드 (예: LOG-001)
    private String message;  // 에러 메시지
    private LocalDateTime timestamp;
}
