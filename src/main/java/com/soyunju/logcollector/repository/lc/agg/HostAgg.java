package com.soyunju.logcollector.repository.lc.agg;

public interface HostAgg {
    String getLogHash();
    Integer getHostCount();
    Integer getRepeatCount();
    String getServiceName();
}