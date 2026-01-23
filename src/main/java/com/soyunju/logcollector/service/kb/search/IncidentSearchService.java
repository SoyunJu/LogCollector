package com.soyunju.logcollector.service.kb.search;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.QIncident;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.dto.kb.IncidentRankResponse;
import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, transactionManager = "kbTransactionManager")
public class IncidentSearchService {

    private final JPAQueryFactory queryFactory;
    private final ErrorLogHostRepository errorLogHostRepository;
    private final IncidentRepository incidentRepository;
    private static final QIncident incident = QIncident.incident;

    // Repository 및 QueryFactory 초기화
    public IncidentSearchService(@Qualifier("kbQueryFactory") JPAQueryFactory queryFactory,
                                 ErrorLogHostRepository errorLogHostRepository,
                                 IncidentRepository incidentRepository) {
        this.queryFactory = queryFactory;
        this.errorLogHostRepository = errorLogHostRepository;
        this.incidentRepository = incidentRepository;
    }

    // Querydsl 리포지토리를 호출하여 인시던트를 검색하고, LC DB에서 호스트 발생 수를 가져와 DTO로 결합함 (In-Memory Join)
    public Page<IncidentResponse> searchIncidents(
            com.soyunju.logcollector.dto.kb.IncidentSearch search,
            Pageable pageable) {

        Page<Incident> page = incidentRepository.search(search, pageable);
        List<String> hashes = page.getContent().stream().map(Incident::getLogHash).toList();

        // 1. LC DB에서 호스트 통계 맵 획득 (HostAgg 타입 사용)
        Map<String, Long> hostCountMap = fetchHostCountMap(hashes);

        // 2. 결과 매핑
        return page.map(i -> IncidentResponse.from(i, hostCountMap.getOrDefault(i.getLogHash(), 0L)));
    }


    // 호스트 발생 수 기준 랭킹 조회. 물리 DB가 다르므로 LC DB에서 상위 해시를 먼저 뽑은 후 KB 데이터를 매핑함
    public List<IncidentRankResponse> topByHostCount(int limit, String serviceName, LocalDateTime from, LocalDateTime to) {
        // 1. LC DB(ErrorLogHost)에서 통계 데이터 추출
        List<com.soyunju.logcollector.repository.lc.agg.HostAgg> topHashes = errorLogHostRepository.findTopHashesByHostCount(limit);

        List<String> hashes = topHashes.stream().map(com.soyunju.logcollector.repository.lc.agg.HostAgg::getLogHash).toList();

        // 2. KB DB(Incident) 정보 매핑
        Map<String, Incident> incidentMap = incidentRepository.findAllByLogHashIn(new HashSet<>(hashes))
                .stream().collect(Collectors.toMap(Incident::getLogHash, v -> v));

        return topHashes.stream()
                .map(r -> {
                    Incident inc = incidentMap.get(r.getLogHash());
                    return inc != null ? IncidentRankResponse.from("hostCount", r.getHostCount().longValue(), inc, r.getHostCount().longValue()) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // LC DB의 ErrorLogHost 테이블에서 해시별 호스트 수를 집계하여 맵으로 반환함
    private Map<String, Long> fetchHostCountMap(List<String> logHashes) {
        if (logHashes.isEmpty()) return Collections.emptyMap();
        List<com.soyunju.logcollector.repository.lc.agg.HostAgg> rows = errorLogHostRepository.countHostsByLogHash(logHashes);
        return rows.stream().collect(Collectors.toMap(
                com.soyunju.logcollector.repository.lc.agg.HostAgg::getLogHash,
                r -> r.getHostCount() != null ? r.getHostCount().longValue() : 0L
        ));
    }

    // 반복 발생 횟수(repeatCount) 기준으로 상위 인시던트를 조회하며, LC DB에서 호스트 카운트 정보를 가져와 DTO를 완성함
    private List<IncidentRankResponse> topByRepeatCount(
            int limit, String serviceName, IncidentStatus status, LocalDateTime from, LocalDateTime to
    ) {
        List<Incident> incidents = queryFactory.selectFrom(incident)
                .where(
                        serviceNameEq(incident, serviceName),
                        statusEq(incident, status),
                        lastOccurredBetween(incident, from, to)
                )
                .orderBy(incident.repeatCount.desc(), incident.lastOccurredAt.desc())
                .limit(limit)
                .fetch();

        Map<String, Long> hostCountMap = fetchHostCountMap(incidents.stream().map(Incident::getLogHash).toList());

        return incidents.stream()
                .map(inc -> IncidentRankResponse.from(
                        "repeatCount",
                        inc.getRepeatCount().longValue(),
                        inc,
                        hostCountMap.getOrDefault(inc.getLogHash(), 0L)
                ))
                .toList();
    }

    // 마지막 발생 시각(lastOccurredAt) 기준으로 상위 인시던트를 조회하며, LC DB에서 호스트 카운트 정보를 결합함
    private List<IncidentRankResponse> topByLastOccurredAt(
            int limit, String serviceName, IncidentStatus status, LocalDateTime from, LocalDateTime to
    ) {
        List<Incident> incidents = queryFactory.selectFrom(incident)
                .where(
                        serviceNameEq(incident, serviceName),
                        statusEq(incident, status),
                        lastOccurredBetween(incident, from, to)
                )
                .orderBy(incident.lastOccurredAt.desc())
                .limit(limit)
                .fetch();

        Map<String, Long> hostCountMap = fetchHostCountMap(incidents.stream().map(Incident::getLogHash).toList());

        return incidents.stream()
                .map(inc -> IncidentRankResponse.from(
                        "lastOccurredAt",
                        inc.getLastOccurredAt() == null ? null
                                : inc.getLastOccurredAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        inc,
                        hostCountMap.getOrDefault(inc.getLogHash(), 0L)
                ))
                .toList();
    }

    // 서비스명 eq
    private com.querydsl.core.types.dsl.BooleanExpression serviceNameEq(QIncident i, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) return null;
        return i.serviceName.eq(serviceName);
    }

    // status eq
    private com.querydsl.core.types.dsl.BooleanExpression statusEq(QIncident i, com.soyunju.logcollector.domain.kb.enums.IncidentStatus status) {
        if (status == null) return null;
        return i.status.eq(status);
    }

    // between eq
    private com.querydsl.core.types.dsl.BooleanExpression lastOccurredBetween(QIncident i, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        if (from == null && to == null) return null;
        if (from != null && to != null) return i.lastOccurredAt.between(from, to);
        if (from != null) return i.lastOccurredAt.goe(from);
        return i.lastOccurredAt.loe(to);
    }

}