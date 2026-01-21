package com.soyunju.logcollector.service.kb.autopolicy;

import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DraftPolicyService {

    private final ErrorLogHostRepository errorLogHostRepository; // LC
    private final DraftPolicyKbWriter draftPolicyKbWriter;       // KB

    @Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;

    @Value("${draft.policy.high-recur-threshold:100}")
    private int highRecurThreshold;

    @Transactional(transactionManager = "lcTransactionManager", readOnly = true)
    public DraftRunResult runAutoDraft() {
        List<HostAgg> aggs = errorLogHostRepository.aggregateByLogHash();
        if (aggs == null || aggs.isEmpty()) {
            return DraftRunResult.empty();
        }

        List<DraftCandidate> candidates = aggs.stream()
                .filter(Objects::nonNull)
                .map(a -> new DraftCandidate(
                        a.getLogHash(),
                        a.getHostCount() == null ? 0 : a.getHostCount(),
                        a.getRepeatCount() == null ? 0 : a.getRepeatCount()
                ))
                .filter(c -> c.logHash() != null)
                .toList();

        if (candidates.isEmpty()) {
            return DraftRunResult.empty();
        }

        return draftPolicyKbWriter.applyCandidates(candidates, hostSpreadThreshold, highRecurThreshold);
    }

    public record DraftCandidate(String logHash, int hostCount, int repeatCount) {}

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
