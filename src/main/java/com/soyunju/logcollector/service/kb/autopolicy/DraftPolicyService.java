package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.monitornig.LcMetrics;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DraftPolicyService {

    private final ErrorLogHostRepository errorLogHostRepository;
    private final IncidentRepository incidentRepository;
    private final KbArticleService kbArticleService;
    private final LcMetrics lcMetrics;

    // 영향 호스트 count
    @Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;
    // repeat Count
    @Value("${draft.policy.high-recur-threshold:100}")
    private int highRecurThreshold;

    @Transactional
    public int runAutoDraft() {
        List<HostAgg> aggs = errorLogHostRepository.aggregateByLogHash();
        if (aggs == null || aggs.isEmpty()) {
            lcMetrics.incAutoDraft("empty");
            return 0;
        }

        Set<String> logHashes = aggs.stream()
                .map(HostAgg::getLogHash)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (logHashes.isEmpty()) {
            lcMetrics.incAutoDraft("empty");
            return 0;
        }

        Map<String, Incident> incidentByHash = incidentRepository.findAllByLogHashIn(logHashes).stream()
                .collect(Collectors.toMap(Incident::getLogHash, it -> it));

        int created = 0;
        int skippedAlreadyExists = 0;
        int skippedNoIncident = 0;
        int skippedNotMatched = 0;

        for (HostAgg agg : aggs) {
            String logHash = agg.getLogHash();
            if (logHash == null) continue;

            int hostCount = (agg.getHostCount() == null) ? 0 : agg.getHostCount();
            int repeatCount = (agg.getRepeatCount() == null) ? 0 : agg.getRepeatCount();

            Incident incident = incidentByHash.get(logHash);
            if (incident == null) {
                skippedNoIncident++;
                continue;
            }

            boolean matched = (hostCount >= hostSpreadThreshold) || (repeatCount >= highRecurThreshold);
            if (!matched) {
                skippedNotMatched++;
                continue;
            }
            // 이미 draft 가 있으면 skip
            if (kbArticleService.findActiveSystemDraftId(incident.getId()).isPresent()) {
                skippedAlreadyExists++;
                continue;
            }
            // draft 생성
            kbArticleService.createSystemDraftIfAbsent(incident.getId());
            created++;
        }

        // 메트릭 (하나의 result 라벨로 뭉치지 않고, 의미 단위로 카운트)
        if (created > 0) {
            for (int i = 0; i < created; i++) lcMetrics.incAutoDraft("created");
        }
        if (skippedAlreadyExists > 0) {
            for (int i = 0; i < skippedAlreadyExists; i++) lcMetrics.incAutoDraft("skipped_exists");
        }
        if (skippedNoIncident > 0) {
            for (int i = 0; i < skippedNoIncident; i++) lcMetrics.incAutoDraft("skipped_no_incident");
        }
        if (skippedNotMatched > 0) {
            for (int i = 0; i < skippedNotMatched; i++) lcMetrics.incAutoDraft("skipped_not_matched");
        }

        return created;
    }
}
