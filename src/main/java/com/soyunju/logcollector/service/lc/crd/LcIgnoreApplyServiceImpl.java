package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LcIgnoreApplyServiceImpl implements LcIgnoreApplyService {

    private final ErrorLogRepository errorLogRepository;

    @Transactional(transactionManager = "lcTransactionManager")
    @Override
    public void applyIgnore(String logHash) {
        //  IGNORED 마킹
        errorLogRepository.markIgnoredByLogHash(logHash);
    }

    @Transactional(transactionManager = "lcTransactionManager")
    @Override
    public void applyUnignore(String logHash) {
        // IGNORED 해제시 NEW
        errorLogRepository.unmarkIgnoredByLogHash(logHash);
    }
}
