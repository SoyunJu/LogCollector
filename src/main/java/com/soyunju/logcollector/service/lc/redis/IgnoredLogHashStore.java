package com.soyunju.logcollector.service.lc.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class IgnoredLogHashStore {

    private static final String KEY = "lc:ignored:log_hash";
    private final StringRedisTemplate redisTemplate;

    public IgnoredLogHashStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isIgnored(String logHash) {
        Boolean member = redisTemplate.opsForSet().isMember(KEY, logHash);
        return Boolean.TRUE.equals(member);
    }

    public void ignore(String logHash) {
        redisTemplate.opsForSet().add(KEY, logHash);
    }

    public void unignore(String logHash) {
        redisTemplate.opsForSet().remove(KEY, logHash);
    }
}
