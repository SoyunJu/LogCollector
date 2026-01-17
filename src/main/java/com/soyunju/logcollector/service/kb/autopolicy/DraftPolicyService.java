package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.domain.kb.Incident;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DraftPolicyService {

    private final ErrorLogHostRepository errorLogHostRepository;
    private final IncidentRepository incidentRepository;
    private final KbArticleService kbArticleService;

    @Transactional
    public int runAutoDraft() {
        List<HostAgg> aggs = errorLogHostRepository.aggregateByLogHash();
        int created = 0;

        Set<String> logHashes = aggs.stream()
                .map(HostAgg::getLogHash)
                .collect(Collectors.toSet());

        Map<String, Incident> incidentByLogHash = incidentRepository.findAllByLogHashIn(logHashes).stream()
                .collect(Collectors.toMap(Incident::getLogHash, it -> it));

        for (HostAgg agg : aggs) {
            String logHash = agg.getLogHash();
            int hostCount = (agg.getHostCount() == null) ? 0 : agg.getHostCount();
            int repeatCount = (agg.getRepeatCount() == null) ? 0 : agg.getRepeatCount();

            Incident incident = incidentByLogHash.get(logHash);
            if (incident == null) continue;

            // 정책 트리거 시 자동 초안 생성 호출
            if (hostCount >= hostSpreadThreshold || repeatCount >= highRecurThreshold) {
                kbArticleService.createSystemDraft(incident.getId());
                created++;
            }
        }
        return created;
    }

    @Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;

    @Value("${draft.policy.high-recur-threshold:10}")
    private int highRecurThreshold;
}