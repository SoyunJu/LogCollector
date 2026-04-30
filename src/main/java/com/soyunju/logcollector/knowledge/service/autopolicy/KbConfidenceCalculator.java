package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class KbConfidenceCalculator {

    // private static final long ARCHIVED_MONTHS = 6;

    /**
     * @param kb           KbArticle 엔티티
     * @param addendumCount addendum 총 개수
     * @param repeatCount  incident.repeatCount
     * @param errorLevel   incident.errorLevel
     * @param lastOccurredAt incident.lastOccurredAt
     */
    public int calculate(KbArticle kb,
                         int addendumCount,
                         int repeatCount,
                         ErrorLevel errorLevel,
                         LocalDateTime lastOccurredAt) {

        KbStatus status = kb.getStatus();

        // 1. DRAFT → 무조건 1
        if (status == KbStatus.DRAFT) {
            return 1;
        }

        // 2. ARCHIVED → 무조건 5
        if (status == KbStatus.ARCHIVED) {
            return 5;
        }

        // 3. IN_PROGRESS → 2
        if (status == KbStatus.IN_PROGRESS) {
            return 2;
        }

        // 4. PUBLISHED → 3~4
        if (status == KbStatus.PUBLISHED) {
            return calcPublishedLevel(addendumCount, repeatCount, errorLevel);
        }
        return 1;
    }


    // Helper Method

    private int calcPublishedLevel(int addendumCount, int repeatCount, ErrorLevel errorLevel) {
        int score = 0;

        if (addendumCount >= 3) score++;
        if (repeatCount >= 5)   score++;
        if (isSevere(errorLevel)) score++;

        return (score >= 2) ? 4 : 3;
    }

    /**
     * 6개월간 마지막 발생 + 마지막 KB 활동이 없었는지 체크
    private boolean isStable(KbArticle kb, LocalDateTime lastOccurredAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastActivity = kb.getLastActivityAt();

        boolean incidentStale = (lastOccurredAt == null)
                || ChronoUnit.MONTHS.between(lastOccurredAt, now) >= ARCHIVED_MONTHS;

        boolean kbStale = (lastActivity == null)
                || ChronoUnit.MONTHS.between(lastActivity, now) >= ARCHIVED_MONTHS;

        return incidentStale && kbStale;
    }
     */

    private boolean isSevere(ErrorLevel level) {
        if (level == null) return false;
        return level == ErrorLevel.FATAL
                || level == ErrorLevel.CRITICAL
                || level == ErrorLevel.EXCEPTION;
    }
}
