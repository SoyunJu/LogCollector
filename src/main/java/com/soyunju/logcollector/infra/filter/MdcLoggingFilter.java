package com.soyunju.logcollector.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 요청에 MDC 컨텍스트를 주입 -> 요청 종료 후 MDC clear
*/
@Order(1)
@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String HEADER_SOURCE  = "X-Source";
    private static final String HEADER_TRACE   = "X-Trace-Id"; // 외부 traceId 전파 허용

    private static final String MDC_TRACE_ID   = "traceId";
    private static final String MDC_METHOD     = "method";
    private static final String MDC_URI        = "uri";
    private static final String MDC_SOURCE     = "source";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            setMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 재사용 방지 -> clear
            MDC.clear();
        }
    }

    private void setMdc(HttpServletRequest request) {
        MDC.put(MDC_TRACE_ID, resolveTraceId(request));
        MDC.put(MDC_METHOD,   request.getMethod());
        MDC.put(MDC_URI,      request.getRequestURI());
        MDC.put(MDC_SOURCE,   resolveSource(request));
    }


    // if trace_id == null, make uuid
    private String resolveTraceId(HttpServletRequest request) {
        String external = request.getHeader(HEADER_TRACE);
        if (external != null && !external.isBlank()) {
            return external;
        }
        return UUID.randomUUID().toString();
    }


    private String resolveSource(HttpServletRequest request) {
        String source = request.getHeader(HEADER_SOURCE);
        if (source != null && !source.isBlank()) {
            return source;
        }
        return "DIRECT";    // default
    }
}