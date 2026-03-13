package com.soyunju.logcollector.dto.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class LogSavedEvent {

    private final String logHash;
    private final String serviceName;
    private final String summary;
    private final String stackTrace;
    private final String errorCode;
    private final String effectiveLevel;
    private final LocalDateTime occurredTime;
    private final int impactedHostCount;
    private final int repeatCount;
    private final Long incidentId;      // Draft 생성 여부 판단용
    private final boolean draftNeeded;  // hostSpread/highRecur 조건 충족 여부
    private final String draftReason;   // "HOST_SPREAD" | "HIGH_RECUR"

}
