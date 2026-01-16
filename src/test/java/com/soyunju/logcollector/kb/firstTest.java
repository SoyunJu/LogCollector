package com.soyunju.logcollector.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.kb.KbTagRepository;
import com.soyunju.logcollector.service.kb.KbArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
class firstTest extends MariaDbContainerTestBase {

    @Autowired
    IncidentRepository incidentRepository;
    @Autowired
    KbArticleRepository kbArticleRepository;
    @Autowired
    KbAddendumRepository kbAddendumRepository;
    @Autowired
    KbTagRepository kbTagRepository;

    @Autowired
    KbArticleService kbArticleService;

    private Long incidentId;

    @BeforeEach
    void setUp() {
        // incident 데이터 준비
        Incident inc = Incident.builder()
                .logHash("hash-001")
                .serviceName("svc-a")
                .summary("ERROR ... 30 lines ...")
                .stackTrace("stacktrace ...")
                .errorCode("E001")
                .errorLevel(ErrorLevel.ERROR)
                .status(IncidentStatus.OPEN)
                .firstOccurredAt(LocalDateTime.now().minusHours(1))
                .lastOccurredAt(LocalDateTime.now().minusMinutes(1))
                .repeatCount(3)
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        incidentId = incidentRepository.save(inc).getId();
    }

    @Test
    @Transactional
    void draft_create_addendum_tag_get_should_work() {
        Long kbId = kbArticleService.createDraft(
                incidentId,
                "DB Timeout으로 응답 지연",
                "", // 빈 값이면 템플릿 생성
                null
        );

        assertThat(kbId).isNotNull();
        assertThat(kbArticleRepository.findById(kbId)).isPresent();

        kbArticleService.addAddendum(kbId, "원인: 커넥션 풀 고갈, 조치: 풀/타임아웃 조정", null);

        assertThat(kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtAsc(kbId)).hasSize(1);

        kbArticleService.setTags(kbId, List.of("db", "timeout", "connection-pool"));

        // 태그가 실제 저장됐는지
        assertThat(kbTagRepository.findByKeyword("db")).isPresent();

        KbArticleResponse res = kbArticleService.getArticle(kbId);

        assertThat(res.getIncidentId()).isEqualTo(incidentId);
        assertThat(res.getIncidentTitle()).isEqualTo("DB Timeout으로 응답 지연");
        assertThat(res.getAddendums()).hasSize(1);
        assertThat(res.getTags()).contains("db", "timeout", "connection-pool");
    }
}