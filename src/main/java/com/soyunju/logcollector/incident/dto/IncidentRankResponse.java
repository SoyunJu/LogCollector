package com.soyunju.logcollector.dto.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentRankResponse {

    private String metric;
    private Long metricValue;

    private Long id;
    private String logHash;
    private String serviceName;
    private IncidentStatus status;
    private ErrorLevel errorLevel;

    private Integer repeatCount;
    private Long hostCount;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
    private LocalDateTime resolvedAt;

    private String summary;
    private String errorCode;

    public static IncidentRankResponse from(String metric, Long metricValue, Incident incident, Long hostCount) {
        return IncidentRankResponse.builder()
                .metric(metric)
                .metricValue(metricValue)
                .id(incident.getId())
                .logHash(incident.getLogHash())
                .serviceName(incident.getServiceName())
                .status(incident.getStatus())
                .errorLevel(incident.getErrorLevel())
                .repeatCount(incident.getRepeatCount())
                .hostCount(hostCount)
                .firstOccurredAt(incident.getFirstOccurredAt())
                .lastOccurredAt(incident.getLastOccurredAt())
                .resolvedAt(incident.getResolvedAt())
                .summary(incident.getSummary())
                .errorCode(incident.getErrorCode())
                .build();
    }
}
