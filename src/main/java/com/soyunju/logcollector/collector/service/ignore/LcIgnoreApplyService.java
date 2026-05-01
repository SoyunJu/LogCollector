package com.soyunju.logcollector.collector.service.ignore;

public interface LcIgnoreApplyService {
    void applyIgnore(String logHash);
    void applyUnignore(String logHash);
}
