package com.soyunju.logcollector.service.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.agg.HostAgg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DraftPolicyService {

    private final ErrorLogHostRepository errorLogHostRepository;
    private final IncidentRepository incidentRepository;

    public DraftPolicyService(ErrorLogHostRepository errorLogHostRepository,
                              IncidentRepository incidentRepository) {
        this.errorLogHostRepository = errorLogHostRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public int runAutoDraft() {

        List<HostAgg> aggs =
                errorLogHostRepository.aggregateByLogHash();

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

            if (hostCount >= hostSpreadThreshold) {
                created += createDraftIfNotExists(
                        incident, hostCount, repeatCount, DraftReason.HOST_SPREAD
                );
            }
            if (repeatCount >= highRecurThreshold) {
                created += createDraftIfNotExists(
                        incident, hostCount, repeatCount, DraftReason.HIGH_RECUR
                );
            }
        }

        return created;
    }

    @Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;

    @Value("${draft.policy.high-recur-threshold:10}")
    private int highRecurThreshold;

    /**
     * TODO: 실제 Draft 생성/중복 방지 로직을 프로젝트 구조에 맞게 구현해야 함.
     * 현재는 컴파일/실행을 위해 최소 형태로만 둠.
     *
     * @return 생성되면 1, 아니면 0 (정책에 맞게 변경)
     */
    private int createDraftIfNotExists(Incident incident,
                                       int hostCount,
                                       int repeatCount,
                                       DraftReason reason) {
        // TODO: Draft 엔티티/리포지토리 연동해서 "이미 Draft 존재하면 skip" 로직 구현
        return 0;
    }


}
