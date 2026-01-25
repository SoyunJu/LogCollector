package com.soyunju.logcollector.service.lc.ignore;

public interface LcIgnoreApplyService {
    void applyIgnore(String logHash);
    void applyUnignore(String logHash);
}
