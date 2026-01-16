package com.soyunju.logcollector.service.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;

public class DraftPolicyService {

    private final ErrorLogHostRepository errorLogHostRepository;

    @Transactional
    public int runAutoDraft() {

        List<HostAggProjection> aggs =
                errorLogHostRepository.aggregateByLogHash();

        int created = 0;

        for (HostAggProjection agg : aggs) {
            String logHash = agg.getLogHash();
            int hostCount = agg.getHostCount();
            int repeatCount = agg.getRepeatCount();

            Incident incident =
                    incidentRepository.findByLogHash(logHash).orElse(null);
            if (incident == null) continue;

            if (hostCount >= HOST_SPREAD_THRESHOLD) {
                created += createDraftIfNotExists(
                        incident, hostCount, repeatCount, DraftReason.HOST_SPREAD
                );
            }

            if (repeatCount >= HIGH_RECUR_THRESHOLD) {
                created += createDraftIfNotExists(
                        incident, hostCount, repeatCount, DraftReason.HIGH_RECUR
                );
            }
        }

        return created;
    }


}
