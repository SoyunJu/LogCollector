package com.soyunju.logcollector.service.kb.search;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.QIncident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.lc.QErrorLogHost;
import com.soyunju.logcollector.dto.kb.IncidentRankResponse;
import com.soyunju.logcollector.dto.kb.IncidentResponse;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.agg.LogHashHostCountAgg;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class IncidentSearchService {

    private final JPAQueryFactory queryFactory;
    private static final QIncident incident = QIncident.incident;

    public IncidentSearchService(@Qualifier("kbQueryFactory") JPAQueryFactory queryFactory,
                                 ErrorLogHostRepository errorLogHostRepository) {
        this.queryFactory = queryFactory;
        this.errorLogHostRepository = errorLogHostRepository;
    }

    private final ErrorLogHostRepository errorLogHostRepository;

    public Page<IncidentResponse> findIncidents(
            Boolean excludeResolved,
            IncidentStatus status,
            ErrorLevel errorLevel,
            String serviceName,
            Boolean serviceNameExact,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        BooleanBuilder condition = new BooleanBuilder();

        // status 우선
        if (status != null) {
            condition.and(incident.status.eq(status));
        } else {
            boolean exResolved = (excludeResolved == null) || excludeResolved;
            if (exResolved) {
                condition.and(incident.status.ne(IncidentStatus.RESOLVED));
            }
        }

        if (errorLevel != null) {
            condition.and(incident.errorLevel.eq(errorLevel));
        }

        if (serviceName != null && !serviceName.isBlank()) {
            boolean exact = (serviceNameExact != null) && serviceNameExact;
            if (exact) {
                condition.and(incident.serviceName.eq(serviceName));
            } else {
                condition.and(incident.serviceName.containsIgnoreCase(serviceName));
            }
        }

        if (from != null) condition.and(incident.lastOccurredAt.goe(from));
        if (to != null) condition.and(incident.lastOccurredAt.loe(to));

        OrderSpecifier<?>[] orderSpecifiers = resolveOrder(pageable);

        List<Incident> incidents = queryFactory
                .selectFrom(incident)
                .where(condition)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(incident.count())
                .from(incident)
                .where(condition)
                .fetchOne();

        Map<String, Long> hostCountByHash = fetchHostCountMap(incidents);

        List<IncidentResponse> content = incidents.stream()
                .map(i -> IncidentResponse.from(i, hostCountByHash.getOrDefault(i.getLogHash(), 0L)))
                .toList();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private Map<String, Long> fetchHostCountMap(List<Incident> incidents) {
        if (incidents == null || incidents.isEmpty()) return Collections.emptyMap();

        List<String> logHashes = incidents.stream()
                .map(Incident::getLogHash)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (logHashes.isEmpty()) return Collections.emptyMap();

        List<LogHashHostCountAgg> rows = errorLogHostRepository.countHostsByLogHash(logHashes);

        Map<String, Long> map = new HashMap<>(rows.size());
        for (LogHashHostCountAgg r : rows) {
            if (r.getLogHash() == null) continue;
            map.put(r.getLogHash(), r.getHostCount() == null ? 0L : r.getHostCount());
        }
        return map;
    }

    private OrderSpecifier<?>[] resolveOrder(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return new OrderSpecifier<?>[]{incident.lastOccurredAt.desc()};
        }

        List<OrderSpecifier<?>> order = new ArrayList<>();
        for (org.springframework.data.domain.Sort.Order o : pageable.getSort()) {
            boolean asc = o.getDirection().isAscending();
            String prop = o.getProperty();

            OrderSpecifier<?> spec = switch (prop) {
                case "lastOccurredAt" -> asc ? incident.lastOccurredAt.asc() : incident.lastOccurredAt.desc();
                case "firstOccurredAt" -> asc ? incident.firstOccurredAt.asc() : incident.firstOccurredAt.desc();
                case "repeatCount" -> asc ? incident.repeatCount.asc() : incident.repeatCount.desc();
                case "status" -> asc ? incident.status.asc() : incident.status.desc();
                case "errorLevel" -> asc ? incident.errorLevel.asc() : incident.errorLevel.desc();
                case "serviceName" -> asc ? incident.serviceName.asc() : incident.serviceName.desc();
                default -> null;
            };
            if (spec != null) order.add(spec);
        }

        if (order.isEmpty()) order.add(incident.lastOccurredAt.desc());
        return order.toArray(new OrderSpecifier<?>[0]);
    }

    public List<IncidentRankResponse> top(
            String metric,
            int limit,
            String serviceName,
            IncidentStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        String m = (metric == null) ? "repeatCount" : metric.trim();
        int size = Math.min(Math.max(limit, 1), 200);

        return switch (m) {
            case "lastOccurredAt" -> topByLastOccurredAt(size, serviceName, status, from, to);
            case "hostCount" -> topByHostCount(size, serviceName, status, from, to);
            case "repeatCount" -> topByRepeatCount(size, serviceName, status, from, to);
            default -> topByRepeatCount(size, serviceName, status, from, to);
        };
    }

    private List<IncidentRankResponse> topByRepeatCount(
            int limit, String serviceName, IncidentStatus status, LocalDateTime from, LocalDateTime to
    ) {
        QIncident i = QIncident.incident;

        List<Incident> incidents = queryFactory.selectFrom(i)
                .where(
                        serviceNameEq(i, serviceName),
                        statusEq(i, status),
                        lastOccurredBetween(i, from, to)
                )
                .orderBy(i.repeatCount.desc(), i.lastOccurredAt.desc())
                .limit(limit)
                .fetch();

        return incidents.stream()
                .map(inc -> IncidentRankResponse.from("repeatCount", inc.getRepeatCount().longValue(), inc, null))
                .toList();
    }

    private List<IncidentRankResponse> topByLastOccurredAt(
            int limit, String serviceName, IncidentStatus status, LocalDateTime from, LocalDateTime to
    ) {
        QIncident i = QIncident.incident;

        List<Incident> incidents = queryFactory.selectFrom(i)
                .where(
                        serviceNameEq(i, serviceName),
                        statusEq(i, status),
                        lastOccurredBetween(i, from, to)
                )
                .orderBy(i.lastOccurredAt.desc())
                .limit(limit)
                .fetch();

        return incidents.stream()
                .map(inc -> IncidentRankResponse.from(
                        "lastOccurredAt",
                        inc.getLastOccurredAt() == null ? null
                                : inc.getLastOccurredAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        inc,
                        null
                ))
                .toList();
    }

    private List<IncidentRankResponse> topByHostCount(
            int limit, String serviceName, IncidentStatus status, LocalDateTime from, LocalDateTime to
    ) {
        QIncident i = QIncident.incident;
        QErrorLogHost eh = QErrorLogHost.errorLogHost;

        List<com.querydsl.core.Tuple> tuples = queryFactory
                .select(i.id, eh.id.count())
                .from(i)
                .join(eh).on(eh.logHash.eq(i.logHash))
                .where(
                        serviceNameEq(i, serviceName),
                        statusEq(i, status),
                        lastOccurredBetween(i, from, to)
                )
                .groupBy(i.id)
                .orderBy(eh.id.count().desc(), i.lastOccurredAt.desc())
                .limit(limit)
                .fetch();

        if (tuples.isEmpty()) return List.of();

        List<Long> ids = tuples.stream()
                .map(t -> t.get(i.id))
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Long> hostCountById = tuples.stream()
                .filter(t -> t.get(i.id) != null)
                .collect(
                        java.util.stream.Collectors.toMap(
                                t -> t.get(i.id),
                                t -> Optional.ofNullable(t.get(eh.id.count())).orElse(0L),
                                (a, b) -> a,
                                LinkedHashMap::new
                        )
                );

        Map<Long, Incident> incidentById = queryFactory
                .selectFrom(i)
                .where(i.id.in(ids))
                .fetch()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Incident::getId, v -> v));

        List<IncidentRankResponse> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Incident incident = incidentById.get(id);
            if (incident == null) continue;
            Long hostCount = hostCountById.getOrDefault(id, 0L);
            out.add(IncidentRankResponse.from("hostCount", hostCount, incident, hostCount));
        }
        return out;
    }

    // 서비스 네임 조회
    private com.querydsl.core.types.dsl.BooleanExpression serviceNameEq(QIncident i, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) return null;
        return i.serviceName.eq(serviceName);
    }

    // 상태 조회
    private com.querydsl.core.types.dsl.BooleanExpression statusEq(QIncident i, IncidentStatus status) {
        if (status == null) return null;
        return i.status.eq(status);
    }

    // 발생 시각 조회 (lastOccured)
    private com.querydsl.core.types.dsl.BooleanExpression lastOccurredBetween(QIncident i, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return null;
        if (from != null && to != null) return i.lastOccurredAt.between(from, to);
        if (from != null) return i.lastOccurredAt.goe(from);
        return i.lastOccurredAt.loe(to);
    }

    private List<OrderSpecifier<?>> toOrderSpecifiers(QIncident i, Sort sort) {
        if (sort == null) return List.of();

        List<OrderSpecifier<?>> list = new ArrayList<>();
        for (Sort.Order o : sort) {
            boolean asc = o.getDirection().isAscending();
            String prop = o.getProperty();

            // 허용 정렬 컬럼만 매핑 (임의 필드 정렬 방지)
            OrderSpecifier<?> spec = switch (prop) {
                case "lastOccurredAt" -> asc ? i.lastOccurredAt.asc() : i.lastOccurredAt.desc();
                case "firstOccurredAt" -> asc ? i.firstOccurredAt.asc() : i.firstOccurredAt.desc();
                case "repeatCount" -> asc ? i.repeatCount.asc() : i.repeatCount.desc();
                case "status" -> asc ? i.status.asc() : i.status.desc();
                case "errorLevel" -> asc ? i.errorLevel.asc() : i.errorLevel.desc();
                case "serviceName" -> asc ? i.serviceName.asc() : i.serviceName.desc();
                default -> null;
            };

            if (spec != null) list.add(spec);
        }
        return list;
    }
}
