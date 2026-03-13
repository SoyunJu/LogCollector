package com.soyunju.logcollector.dto.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class LogResolvedEvent {

    private final String logHash;
    private final Long incidentId;      // Draft 생성용
    private final LocalDateTime resolvedAt;
}
