package com.soyunju.logcollector.dto.kb;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbArticleResponse {
    private Long id;
    private Long incidentId;
    private String incidentTitle;
    private String content;
    private String status;
    private Integer confidenceLevel;
    private String createdBy;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private String title;

    private List<String> tags;      // 키워드 리스트
    private List<AddendumDto> addendums;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AddendumDto {
        private Long id;
        private String content;
        private String createdBy;
        private LocalDateTime createdAt;
    }
}
