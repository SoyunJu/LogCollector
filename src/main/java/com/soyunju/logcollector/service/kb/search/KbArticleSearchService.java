package com.soyunju.logcollector.service.kb.search;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.soyunju.logcollector.domain.kb.KbArticle;
import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import com.soyunju.logcollector.domain.kb.enums.KbStatus;
import com.soyunju.logcollector.dto.kb.KbArticleResponse;
import com.soyunju.logcollector.dto.kb.KbArticleSearch;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.soyunju.logcollector.domain.kb.QKbAddendum.kbAddendum;
import static com.soyunju.logcollector.domain.kb.QKbArticle.kbArticle;

@Slf4j
@Service
@Transactional(readOnly = true, transactionManager = "kbTransactionManager")
public class KbArticleSearchService {

    private final KbArticleRepository kbArticleRepository;
    private final JPAQueryFactory queryFactory;
    private final KbArticleEsService kbArticleEsService;

    public KbArticleSearchService(
            KbArticleRepository kbArticleRepository,
            @Qualifier("kbQueryFactory") JPAQueryFactory queryFactory,
            KbArticleEsService kbArticleEsService
    ) {
        this.kbArticleRepository = kbArticleRepository;
        this.queryFactory = queryFactory;
        this.kbArticleEsService = kbArticleEsService;
    }

    // 전체 목록 조회
    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public Page<KbArticleResponse> findAll(KbArticleSearch kas, Pageable pageable) {

        BooleanBuilder where = new BooleanBuilder();

        if (kas != null) {
            if (kas.getStatus() != null && !kas.getStatus().isBlank()) {
                try {
                    where.and(kbArticle.status.eq(KbStatus.valueOf(kas.getStatus())));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (kas.getCreatedBy() != null && !kas.getCreatedBy().isBlank()) {
                try {
                    where.and(kbArticle.createdBy.eq(CreatedBy.valueOf(kas.getCreatedBy())));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // keyword not null -> ES JPA in
            if (kas.getKeyword() != null && !kas.getKeyword().isBlank()) {
                List<Long> esIds = kbArticleEsService.searchIds(
                        kas.getKeyword(),
                        pageable.getPageNumber(),
                        pageable.getPageSize()
                );

                if (esIds.isEmpty()) {
                    // ES 결과 없음 -> 빈 페이지 즉시 반환
                    return new PageImpl<>(List.of(), pageable, 0);
                }
                where.and(kbArticle.id.in(esIds));
            }
        }

        Long total = queryFactory
                .select(kbArticle.count())
                .from(kbArticle)
                .where(where)
                .fetchOne();

        long totalCount = (total == null) ? 0L : total;

        List<KbArticle> rows = queryFactory
                .selectFrom(kbArticle)
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(kbArticle.createdAt.desc())
                .fetch();

        List<KbArticleResponse> content = rows.stream()
                .map(kb -> KbArticleResponse.builder()
                        .id(kb.getId())
                        .title(kb.getIncidentTitle() != null ? kb.getIncidentTitle() : "시스템 생성 초안 (내용 확인 필요)")
                        .status(kb.getStatus() != null ? kb.getStatus().name() : null)
                        .createdAt(kb.getCreatedAt())
                        .createdBy(kb.getCreatedBy() != null ? kb.getCreatedBy().name() : null)
                        .confidenceLevel(kb.getConfidenceLevel())
                        .build())
                .toList();

        return new PageImpl<>(content, pageable, totalCount);
    }

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public KbArticleResponse getArticle(Long kbArticleId) {
        return getArticle(kbArticleId, 0, 20);
    }

    @Transactional(readOnly = true, transactionManager = "kbTransactionManager")
    public KbArticleResponse getArticle(Long kbArticleId, int addendumPage, int addendumSize) {
        if (addendumPage < 0) addendumPage = 0;
        if (addendumSize <= 0) addendumSize = 20;
        if (addendumSize > 200) addendumSize = 200;

        KbArticle kb = kbArticleRepository.findByIdWithIncident(kbArticleId)
                .orElseThrow(() -> new IllegalArgumentException("Base를 찾을 수 없습니다."));

        long offset = (long) addendumPage * addendumSize;

        Long total = queryFactory
                .select(kbAddendum.count())
                .from(kbAddendum)
                .where(kbAddendum.kbArticle.id.eq(kbArticleId))
                .fetchOne();

        long totalCount = (total == null) ? 0L : total;

        List<com.soyunju.logcollector.domain.kb.KbAddendum> rows = queryFactory
                .selectFrom(kbAddendum)
                .where(kbAddendum.kbArticle.id.eq(kbArticleId))
                .orderBy(kbAddendum.createdAt.desc(), kbAddendum.id.desc())
                .offset(offset)
                .limit(addendumSize + 1L)
                .fetch();

        boolean hasNext = rows.size() > addendumSize;
        if (hasNext) rows = rows.subList(0, addendumSize);

        List<KbArticleResponse.AddendumDto> addendums = rows.stream()
                .map(a -> KbArticleResponse.AddendumDto.builder()
                        .id(a.getId())
                        .content(a.getContent())
                        .createdBy(a.getCreatedBy() != null ? a.getCreatedBy().name() : null)
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return KbArticleResponse.builder()
                .id(kb.getId())
                .incidentId(kb.getIncident() != null ? kb.getIncident().getId() : null)
                .incidentTitle(kb.getIncidentTitle())
                .content(kb.getContent())
                .status(kb.getStatus() != null ? kb.getStatus().name() : null)
                .createdAt(kb.getCreatedAt())
                .createdBy(kb.getCreatedBy() != null ? kb.getCreatedBy().name() : null)
                .serviceName(kb.getIncident() != null ? kb.getIncident().getServiceName() : null)
                .errorCode(kb.getIncident() != null ? kb.getIncident().getErrorCode() : null)
                .addendums(addendums)
                .addendumPage(addendumPage)
                .addendumSize(addendumSize)
                .addendumTotal(totalCount)
                .addendumHasNext(hasNext)
                .build();
    }
}