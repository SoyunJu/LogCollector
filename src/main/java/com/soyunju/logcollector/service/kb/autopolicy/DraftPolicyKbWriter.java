package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.service.kb.autopolicy.DraftPolicyService.DraftCandidate;
import com.soyunju.logcollector.service.kb.autopolicy.DraftPolicyService.DraftRunResult;
import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import com.soyunju.logcollector.service.kb.crd.KbDraftService;
import com.soyunju.logcollector.service.kb.search.KbArticleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftPolicyKbWriter {

    private final IncidentRepository incidentRepository; // KB
    private final KbArticleService kbArticleService;      // KB
    private final KbArticleSearchService kbArticleSearchService;
    private final KbDraftService kbDraftService;

    @Transactional(transactionManager = "kbTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public DraftRunResult applyCandidates(
            java.util.List<DraftCandidate> candidates,
            int hostSpreadThreshold,
            int highRecurThreshold
    ) {
        Set<String> logHashes = candidates.stream()
                .map(DraftCandidate::logHash)
                .collect(Collectors.toSet());

        Map<String, Incident> incidentByHash = incidentRepository.findAllByLogHashIn(logHashes).stream()
                .collect(Collectors.toMap(Incident::getLogHash, Function.identity()));

        int created = 0;
        int skippedExists = 0;
        int skippedNoIncident = 0;
        int skippedNotMatched = 0;
        int failed = 0;

        for (DraftCandidate c : candidates) {
            try {
                Incident incident = incidentByHash.get(c.logHash());
                if (incident == null) {
                    skippedNoIncident++;
                    continue;
                }

                boolean matched =
                        c.hostCount() >= hostSpreadThreshold ||
                                c.repeatCount() >= highRecurThreshold;

                if (!matched) {
                    skippedNotMatched++;
                    continue;
                }

                DraftReason reason = (c.hostCount() >= hostSpreadThreshold)
                        ? DraftReason.HOST_SPREAD
                        : DraftReason.HIGH_RECUR;

                // (선택) 사전 체크: 최종 중복 방지는 system_draft UNIQUE + 서비스 내부에서 처리
                if (kbDraftService.findActiveSystemDraftId(incident.getId()).isPresent()) {
                    skippedExists++;
                    continue;
                }

                Long kbArticleId = kbDraftService.createSystemDraftIfAbsent(
                        incident.getId(),
                        c.hostCount(),
                        c.repeatCount(),
                        reason
                );

                if (kbArticleId != null) {
                    created++;
                } else {
                    // 중복/스킵(null 반환)
                    skippedExists++;
                }

            } catch (Exception e) {
                failed++;
                log.error("[AUTO_DRAFT][FAILED] logHash={} hostCount={} repeatCount={}",
                        c.logHash(), c.hostCount(), c.repeatCount(), e);
            }
        }
        if (failed > 0) {
            log.warn("[AUTO_DRAFT][SUMMARY] created={} failed={} skippedExists={} skippedNoIncident={} skippedNotMatched={}",
                    created, failed, skippedExists, skippedNoIncident, skippedNotMatched);
        } else {
            log.info("[AUTO_DRAFT][SUMMARY] created={} skippedExists={} skippedNoIncident={} skippedNotMatched={}",
                    created, skippedExists, skippedNoIncident, skippedNotMatched);
        }
        return new DraftRunResult(created, skippedExists, skippedNoIncident, skippedNotMatched, failed);
    }
}
