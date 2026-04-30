package com.soyunju.logcollector.dto.kb;

import com.soyunju.logcollector.domain.kb.KbAddendum;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbAddendumResponse {

    private Long id;
    private String content;
    private String createdBy;
    private LocalDateTime createdAt;

    public static KbAddendumResponse from(KbAddendum a) {
        return KbAddendumResponse.builder()
                .id(a.getId())
                .content(a.getContent())
                .createdBy(a.getCreatedBy() != null ? a.getCreatedBy().name() : null)
                .createdAt(a.getCreatedAt())
                .build();
    }



}
