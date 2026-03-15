package com.soyunju.logcollector.dto.logfixer;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LogFixerIncidentPayload {

    private String logHash;
    private String serviceName;
    private String summary;
    private String stackTrace;
    private String errorCode;
    private String logLevel;
    private LocalDateTime occurredTime;
    private int impactedHostCount;
    private int repeatCount;
}