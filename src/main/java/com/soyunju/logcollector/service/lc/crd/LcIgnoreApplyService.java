package com.soyunju.logcollector.service.lc.crd;

public interface LcIgnoreApplyService {
    void applyIgnore(String logHash);
    void applyUnignore(String logHash);
}
