package com.soyunju.logcollector.collector.service.ignore;

import com.soyunju.logcollector.collector.service.redis.IgnoredLogHashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LcIgnoreApplyServiceImpl implements LcIgnoreApplyService {

    private final IgnoredLogHashStore ignoredLogHashStore;

    @Override
    public void applyIgnore(String logHash) {
        ignoredLogHashStore.ignore(logHash);
    }

    @Override
    public void applyUnignore(String logHash) {
        ignoredLogHashStore.unignore(logHash);
    }
}
