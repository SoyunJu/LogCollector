package com.soyunju.logcollector.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 로그 관련 에러
    INVALID_LOG_LEVEL(HttpStatus.BAD_REQUEST, "LOG-001", "수집 대상 로그 레벨이 아닙니다."),
    LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "LOG-002", "존재하지 않는 로그 ID입니다."),

    // 일반 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}