package com.soyunju.logcollector.monitornig;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LcMetrics {

    private final MeterRegistry registry;

    // Redis queue/dlq 길이 gauge
    public void bindRedisQueueGauges(RedisTemplate<String, ?> redisTemplate, LogCollectorRedisProperties props) {
        Gauge.builder("lc.redis.queue.size", redisTemplate, rt -> safeListSize(rt, props.getQueueKey()))
                .description("Redis queue list size")
                .register(registry);

        Gauge.builder("lc.redis.dlq.size", redisTemplate, rt -> safeListSize(rt, props.getDlqKey()))
                .description("Redis DLQ list size")
                .register(registry);
    }

    private double safeListSize(RedisTemplate<String, ?> redisTemplate, String key) {
        try {
            Long size = redisTemplate.opsForList().size(key);
            return size == null ? 0d : size.doubleValue();
        } catch (Exception e) {
            return 0d;
        }
    }

    // ---- Redis enqueue
    public void incRedisEnqueueSuccess() {
        counter("lc.redis.enqueue", "result", "success").increment();
    }

    public void incRedisEnqueueFailure(String reason) {
        counter("lc.redis.enqueue", "result", "failure", "reason", reason).increment();
    }

    public void incDbFallback(String result) {
        counter("lc.redis.db_fallback", "result", result).increment();
    }

    // ---- Consumer
    public void incConsumeProcessed() {
        counter("lc.consumer.processed").increment();
    }

    public void incConsumeFailed(String stage) {
        counter("lc.consumer.failed", "stage", stage).increment();
    }

    public void incDlqPush(String result) {
        counter("lc.consumer.dlq_push", "result", result).increment();
    }

    public void incIgnored() {
        counter("lc.consumer.ignored").increment();
    }

    public Timer.Sample startPersistLagTimer() {
        return Timer.start(registry);
    }

    public void recordPersistLagSeconds(Timer.Sample sample, String result) {
        sample.stop(Timer.builder("lc.persist.lag")
                .description("Approx lag seconds from occurredTime to DB persist")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry));
    }

    // ---- saveLog
    public void incSaveLog(String result) {
        counter("lc.savelog", "result", result).increment();
    }

    // ---- Notifications
    public void incSlackNotify(String type) {
        counter("lc.notify.slack", "type", type).increment();
    }

    // ---- Status transitions
    public void incStatusChange(ErrorStatus from, ErrorStatus to) {
        String f = (from == null) ? "null" : from.name();
        String t = (to == null) ? "null" : to.name();
        counter("lc.errorlog.status_change", "from", f, "to", t).increment();
    }

    // ---- Policy draft
    public void incAutoDraft(String result) {
        counter("lc.kb.auto_draft", "result", result).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
