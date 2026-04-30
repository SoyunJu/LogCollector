package com.soyunju.logcollector.service.kb.crud;

import com.soyunju.logcollector.repository.kb.LcIgnoreOutboxRepository;
import com.soyunju.logcollector.service.lc.ignore.LcIgnoreApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LcIgnoreOutboxProcessorService {

    private final LcIgnoreOutboxRepository outboxRepository;
    private final LcIgnoreApplyService lcIgnoreApplyService;

    @Transactional(transactionManager = "kbTransactionManager")
    public void process(LocalDateTime now) {
    }
}
