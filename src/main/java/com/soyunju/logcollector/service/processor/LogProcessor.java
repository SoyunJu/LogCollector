package com.soyunju.logcollector.service.processor;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class LogProcessor {

    public String extractSummary(String content) {
        if (content == null || content.isEmpty()) return "No content available";
        String[] lines = content.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int startLine = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("ERROR") || lines[i].contains("Exception") || lines[i].contains("FATAL")) {
                startLine = i;
                break;
            }
        }
        for (int i = startLine; i < Math.min(startLine + 30, lines.length); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    public String generateErrorCode(String message) {
        if (message.contains("Database") || message.contains("SQL")) return "DB-ERR-001";
        if (message.contains("Timeout") || message.contains("Connection")) return "NET-ERR-001";
        if (message.contains("NullPointer")) return "SYS-ERR-001";
        return "GEN-ERR-999";
    }

    public boolean isTargetLevel(String level) {
        String upper = level.toUpperCase();
        return upper.equals("ERROR") || upper.equals("CRITICAL") || upper.equals("FATAL");
    }

    // 로그 해싱
    // --- 정규화 패턴(가변값 제거) ---
    private static final Pattern PATTERN_UUID =
            Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern PATTERN_IPV4 =
            Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");

    // ISO timestamp 변형(Z, timezone, 콤마 밀리초 포함)
    private static final Pattern PATTERN_TS_ISO =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?\\b");

    private static final Pattern PATTERN_DATE =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private static final Pattern PATTERN_HEX_0X =
            Pattern.compile("\\b0x[0-9a-fA-F]+\\b");

    // traceId/requestId류 (키=값 형태)
    private static final Pattern PATTERN_KV_ID =
            Pattern.compile("\\b(?i)(traceid|spanid|requestid|correlationid|txid|transactionid|sessionid|rid)\\s*[:=]\\s*([A-Za-z0-9._\\-]{6,})\\b");

    // 자바 stack trace 라인번호 (Some.java:123)
    private static final Pattern PATTERN_JAVA_LINE =
            Pattern.compile("(\\.java:)(\\d+)\\b");

    private static final Pattern PATTERN_WS =
            Pattern.compile("\\s+");

    public String normalizeMessage(String message) {
        if (message == null) return "";

        String s = message;

        s = PATTERN_UUID.matcher(s).replaceAll("<UUID>");
        s = PATTERN_KV_ID.matcher(s).replaceAll("$1=<ID>");
        s = PATTERN_IPV4.matcher(s).replaceAll("<IP>");
        s = PATTERN_TS_ISO.matcher(s).replaceAll("<TS>");
        s = PATTERN_DATE.matcher(s).replaceAll("<DATE>");
        s = PATTERN_HEX_0X.matcher(s).replaceAll("<HEX>");

        s = PATTERN_WS.matcher(s).replaceAll(" ").trim();
        return s;
    }

    private String normalizeStackTop(String stackTrace, int maxLines) {
        if (stackTrace == null || stackTrace.isBlank()) return "";

        String[] lines = stackTrace.split("\\R");
        List<String> top = new ArrayList<>(maxLines);

        for (String line : lines) {
            if (top.size() >= maxLines) break;
            String l = line.trim();
            if (l.isBlank()) continue;

            l = PATTERN_JAVA_LINE.matcher(l).replaceAll("$1<LINE>");
            l = normalizeMessage(l);

            top.add(l);
        }
        return String.join(" / ", top);
    }

    /**
     * incident hash: "서비스명 + 정규화된 메시지 + (스택 상단 일부)"
     * - host/ip는 넣지 않음(영향도는 error_log_hosts에서 관리)
     */
    public String generateIncidentHash(String serviceName, String message, String stackTrace) {
        String normalizedMsg = normalizeMessage(message);
        String normalizedStackTop = normalizeStackTop(stackTrace, 8); // 상단 8줄

        String signature = normalizedStackTop.isBlank()
                ? normalizedMsg
                : (normalizedMsg + " | " + normalizedStackTop);

        signature = signature.toLowerCase();
        signature = PATTERN_WS.matcher(signature).replaceAll(" ").trim();

        return sha256Hex((serviceName == null ? "" : serviceName.trim()) + ":" + signature);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("해시 알고리즘 생성 실패", e);
        }
    }

    public ErrorLogResponse convertToResponse(ErrorLog log) {
        return ErrorLogResponse.builder()
                .logId(log.getId())
                .serviceName(log.getServiceName())
                .summary(log.getSummary())
                .errorCode(log.getErrorCode())
                .hostInfo(log.getHostName())
                .build();
    }

    public ErrorLogResponse convertToResponse(ErrorLog log, long impactedHostCount) {
        return ErrorLogResponse.builder()
                .logId(log.getId())
                .serviceName(log.getServiceName())
                .summary(log.getSummary())
                .errorCode(log.getErrorCode())
                .hostInfo(log.getHostName())
                .impactedHostCount(impactedHostCount)
                .build();
    }


}
