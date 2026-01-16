package com.soyunju.logcollector.service.scheduler;

import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
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

    @Scheduled(cron = "0 10 3 * * *") // 매일 03:10
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
