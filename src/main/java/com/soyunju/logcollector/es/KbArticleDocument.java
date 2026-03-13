package com.soyunju.logcollector.es;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "kb_articles")
@Setting(settingPath = "es/kb-article-settings.json")
public class KbArticleDocument {

    @Id
    private String id; // KbArticle.id (Long → String)

    @Field(type = FieldType.Text, analyzer = "standard")
    private String incidentTitle;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    // addendum 내용을 Text 로 합쳐서 저장
    @Field(type = FieldType.Text, analyzer = "standard")
    private String addendumContent;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    @Field(type = FieldType.Keyword)
    private String errorCode;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;
}