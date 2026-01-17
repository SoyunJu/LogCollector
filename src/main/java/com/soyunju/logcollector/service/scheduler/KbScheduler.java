package com.soyunju.logcollector.service.scheduler;

import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.service.kb.autopolicy.DraftPolicyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KbScheduler {

    private final KbArticleRepository kbArticleRepository;
    private final DraftPolicyService draftPolicyService; // 추가된 부분만 남깁니다.

    @Scheduled(cron = "0 0/30 * * * *") // 정책 검사 스케줄 추가
    @Transactional
    public void runAutoDrafting() {
        draftPolicyService.runAutoDraft();
    }

    @Scheduled(cron = "0 10 3 * * *") // 매일 03:10 기존 로직
    @Transactional
    public void promoteDefinite() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);

        kbArticleRepository.bulkSetStatusByLastActivity(
                KbStatus.DEFINITE,
                List.of(KbStatus.OPEN, KbStatus.UNDERWAY, KbStatus.RESPONDED),
                cutoff
        );
    }
}
