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
public class IncidentResponse {

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
    private String createdBy;

    public static IncidentResponse from(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .logHash(incident.getLogHash())
                .serviceName(incident.getServiceName())
                .status(incident.getStatus())
                .errorLevel(incident.getErrorLevel())
                .repeatCount(incident.getRepeatCount())
                .hostCount(null)
                .firstOccurredAt(incident.getFirstOccurredAt())
                .lastOccurredAt(incident.getLastOccurredAt())
                .resolvedAt(incident.getResolvedAt())
                .summary(incident.getSummary())
                .errorCode(incident.getErrorCode())
                .build();
    }

    public static IncidentResponse from(Incident incident, Long hostCount) {
        return IncidentResponse.builder()
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
