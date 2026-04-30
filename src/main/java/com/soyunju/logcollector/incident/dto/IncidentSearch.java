package com.soyunju.logcollector.dto.kb;

import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IncidentSearch {
    private String serviceName;
    private ErrorLevel level;
    private IncidentStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String keyword;
}

