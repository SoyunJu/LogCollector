package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DraftPolicyService {

    private static final int BATCH_SIZE = 500;

    private final ErrorLogHostRepository errorLogHostRepository; // LC
    private final IncidentRepository incidentRepository;         // KB
    private final DraftPolicyKbWriter draftPolicyKbWriter;       // KB

    @Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;

    @Value("${draft.policy.high-recur-threshold:100}")
    private int highRecurThreshold;

    public DraftRunResult runAutoDraft() {
        List<String> activeLogHashes = fetchActiveIncidentLogHashes();
        if (activeLogHashes == null || activeLogHashes.isEmpty()) {
            return DraftRunResult.empty();
        }
        // LC 집계 결과를 logHash -> HostAgg 맵으로 구성
        Map<String, HostAgg> aggByHash = fetchHostAggByLogHashes(activeLogHashes);

        List<DraftCandidate> candidates = activeLogHashes.stream()
                .filter(Objects::nonNull)
                .map(logHash -> {
                    HostAgg a = aggByHash.get(logHash);
                    int hostCount = (a == null || a.getHostCount() == null) ? 1 : a.getHostCount();
                    int repeatCount = (a == null || a.getRepeatCount() == null) ? 1 : a.getRepeatCount();
                    return new DraftCandidate(logHash, hostCount, repeatCount);
                })
                .filter(c -> c.logHash() != null)
                .toList();

        if (candidates.isEmpty()) {
            return DraftRunResult.empty();
        }
        return draftPolicyKbWriter.applyCandidates(candidates, hostSpreadThreshold, highRecurThreshold);
    }

    @Transactional(transactionManager = "kbTransactionManager", readOnly = true)
    protected List<String> fetchActiveIncidentLogHashes() {
        return incidentRepository.findNotResolvedLogHash(IncidentStatus.RESOLVED);
    }

    @Transactional(transactionManager = "lcTransactionManager", readOnly = true)
    protected Map<String, HostAgg> fetchHostAggByLogHashes(List<String> logHashes) {
        if (logHashes == null || logHashes.isEmpty()) return Collections.emptyMap();

        Map<String, HostAgg> result = new HashMap<>();

        for (int i = 0; i < logHashes.size(); i += BATCH_SIZE) {
            List<String> batch = logHashes.subList(i, Math.min(i + BATCH_SIZE, logHashes.size()));
            List<HostAgg> aggs = errorLogHostRepository.aggregateByLogHash(batch);
            if (aggs == null || aggs.isEmpty()) continue;

            for (HostAgg agg : aggs) {
                if (agg == null || agg.getLogHash() == null) continue;
                result.put(agg.getLogHash(), agg);
            }
        }
        return result;
    }


    public record DraftCandidate(String logHash, int hostCount, int repeatCount) {
    }

    public record DraftRunResult(
            int created,
            int skippedExists,
            int skippedNoIncident,
            int skippedNotMatched,
            int failed
    ) {
        public static DraftRunResult empty() {
            return new DraftRunResult(0, 0, 0, 0, 0);
        }
    }
}
