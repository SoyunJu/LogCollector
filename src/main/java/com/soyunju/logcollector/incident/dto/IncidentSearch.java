package com.soyunju.logcollector.incident.dto;


import com.soyunju.logcollector.collector.domain.enums.ErrorLevel;
import com.soyunju.logcollector.incident.domain.enums.IncidentStatus;
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

