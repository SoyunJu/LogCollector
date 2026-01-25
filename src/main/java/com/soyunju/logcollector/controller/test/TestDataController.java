package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/test/data")
public class TestDataController {

    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogHostRepository errorLogHostRepository;

    private final IncidentRepository incidentRepository;
    private final KbArticleRepository kbArticleRepository;

    // KB 트랜잭션/영속성 유닛으로 native delete 실행
    @PersistenceContext(unitName = "kb")
    private EntityManager kbEntityManager;

    /**
     * 테스트용: 특정 logHash 기준으로 LC/KB 데이터를 정리.
     * 주의: 운영에 노출 금지. (test 패키지 + 필요시 security/프로필로 제한)
     */
    @DeleteMapping("/by-log-hash/{logHash}")
    public ResponseEntity<Void> deleteByLogHash(@PathVariable String logHash) {
        // LC/KB가 분리 트랜잭션이므로, 개별 트랜잭션으로 처리
        deleteLcByLogHash(logHash);
        deleteKbByLogHash(logHash);
        return ResponseEntity.noContent().build();
    }

    @Transactional(transactionManager = "lcTransactionManager")
    public void deleteLcByLogHash(String logHash) {
        // host 먼저 삭제(외래키/정합성)
        errorLogHostRepository.deleteByLogHash(logHash);
        errorLogRepository.deleteByLogHash(logHash);
    }

    @Transactional(transactionManager = "kbTransactionManager")
    public void deleteKbByLogHash(String logHash) {

        // 1) system_draft -> incident FK 때문에 incident 삭제 전 자식부터 제거
        // MariaDB multi-table delete 사용
        kbEntityManager.createNativeQuery("""
                DELETE sd
                FROM system_draft sd
                JOIN incident i ON sd.incident_id = i.id
                WHERE i.log_hash = :logHash
                """)
                .setParameter("logHash", logHash)
                .executeUpdate();

        // 2) outbox도 테스트 데이터면 같이 정리
        kbEntityManager.createNativeQuery("""
                DELETE FROM lc_ignore_outbox
                WHERE log_hash = :logHash
                """)
                .setParameter("logHash", logHash)
                .executeUpdate();

        // 3) KBArticle이 incident FK를 잡고 있으면 KBArticle 먼저 삭제
        kbArticleRepository.deleteByIncident_LogHash(logHash);

        // 4) 마지막에 incident 삭제
        incidentRepository.deleteByLogHash(logHash);
    }
}
