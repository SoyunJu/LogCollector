package com.soyunju.logcollector.collector.repository.agg;

public interface HostAgg {
    String getLogHash();
    Integer getHostCount();
    Integer getRepeatCount();
    String getServiceName();
}