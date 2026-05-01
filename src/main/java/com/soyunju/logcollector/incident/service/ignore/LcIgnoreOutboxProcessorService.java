package com.soyunju.logcollector.incident.service.ignore;

import com.soyunju.logcollector.incident.repository.LcIgnoreOutboxRepository;
import com.soyunju.logcollector.collector.service.ignore.LcIgnoreApplyService;
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
