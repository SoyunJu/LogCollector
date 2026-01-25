package com.soyunju.logcollector.controller.test;

import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
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
        // KBArticle이 incident FK를 잡고 있으면 KBArticle 먼저 삭제
        kbArticleRepository.deleteByIncident_LogHash(logHash);
        incidentRepository.deleteByLogHash(logHash);
    }
}
