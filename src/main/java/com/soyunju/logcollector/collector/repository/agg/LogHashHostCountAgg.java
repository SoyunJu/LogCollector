package com.soyunju.logcollector.repository.lc.agg;

/**
 * log_hash 별 영향 호스트 수 집계용 Projection
 */
public interface LogHashHostCountAgg {
    String getLogHash();
    Long getHostCount();
}
