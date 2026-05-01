package com.soyunju.logcollector.global.filter;

import com.soyunju.logcollector.global.config.RateLimitProperties;
import com.soyunju.logcollector.global.security.apikey.ApiKeyPrincipal;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;


@Slf4j
@Order(3)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties props;

    private static final String PREFIX_API = "rl:api:";
    private static final String PREFIX_LOGIN = "rl:login:";
    private static final String LOGIN_PATH = "/api/admin/auth/login";

    public RateLimitFilter(RedisClient redisClient, RateLimitProperties props) {
        StatefulRedisConnection<byte[], byte[]> connection =
                redisClient.connect(ByteArrayCodec.INSTANCE);
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (LOGIN_PATH.equals(request.getRequestURI())) {
            if (!consumeLoginBucket(request, response)) return;
            filterChain.doFilter(request, response);
            return;
        }

        // API Key
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ApiKeyPrincipal principal) {
            if (!consumeApiBucket(principal, response)) return;
        }

        filterChain.doFilter(request, response);
    }


    private boolean consumeLoginBucket(HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        String ip = getClientIp(request);
        String key = (PREFIX_LOGIN + ip);

        Bucket bucket = proxyManager.builder()
                .build(key.getBytes(), loginBucketConfig());

        if (bucket.tryConsume(1)) {
            return true;
        }

        log.warn("[RATE_LIMIT][LOGIN] IP 제한 초과: ip={}", ip);
        sendTooManyRequests(response, "로그인 시도 횟수를 초과했습니다. " +
                props.getLogin().getBanMinutes() + "분 후 재시도하세요.");
        return false;
    }


    private boolean consumeApiBucket(ApiKeyPrincipal principal,
                                     HttpServletResponse response) throws IOException {
        String key = PREFIX_API + principal.getTenantId() + ":" + principal.getAppId();

        Supplier<BucketConfiguration> config = () -> planBucketConfig(principal.getPlan());
        Bucket bucket = proxyManager.builder().build(key.getBytes(), config);

        if (bucket.tryConsume(1)) {
            return true;
        }

        log.warn("[RATE_LIMIT][API] Plan 한도 초과: tenantId={}, appId={}, plan={}",
                principal.getTenantId(), principal.getAppId(), principal.getPlan());
        sendTooManyRequests(response, "요청 한도를 초과했습니다. 잠시 후 재시도하세요.");
        return false;
    }


    private Supplier<BucketConfiguration> loginBucketConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.getLogin().getMaxAttempts())
                        .refillIntervally(
                                props.getLogin().getMaxAttempts(),
                                Duration.ofMinutes(props.getLogin().getBanMinutes())
                        )
                        .build())
                .build();
    }

    private BucketConfiguration planBucketConfig(String plan) {
        int rps = switch (plan != null ? plan.toUpperCase() : "FREE") {
            case "PRO" -> props.getPro().getRequestsPerSecond();
            case "ENTERPRISE" -> props.getEnterprise().getRequestsPerSecond();
            default -> props.getFree().getRequestsPerSecond();
        };

        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rps)
                        .refillIntervally(rps, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    // utils

    private void sendTooManyRequests(HttpServletResponse response,
                                     String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}