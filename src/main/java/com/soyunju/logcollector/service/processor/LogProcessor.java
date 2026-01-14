package com.soyunju.logcollector.service.processor;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.util.LogNormalization;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

   /* public String generateErrorCode(String message) {
        if (message == null) return "GEN-ERR-999";
        if (message.contains("Database") || message.contains("SQL")) return "DB-ERR-001";
        if (message.contains("Timeout") || message.contains("Connection")) return "NET-ERR-001";
        if (message.contains("NullPointer")) return "SYS-ERR-001";
        return "GEN-ERR-999";
    } */

    public boolean isTargetLevel(String level) {
        if (level == null) return false;
        String upper = level.toUpperCase();
        return upper.equals("ERROR") || upper.equals("CRITICAL") || upper.equals("FATAL");
    }

    /**
     * incident hash: "서비스명 + 정규화된 메시지 + (스택 상단 일부)"
     * - host/ip는 넣지 않음(영향도는 error_log_hosts에서 관리)
     */
    public String generateIncidentHash(String serviceName, String message, String stackTrace) {
        String normalizedMsg = LogNormalization.normalizeMessage(message);
        String normalizedStackTop = LogNormalization.normalizeStackTop(stackTrace, 8);

        String signature = normalizedStackTop.isBlank()
                ? normalizedMsg
                : (normalizedMsg + " | " + normalizedStackTop);

        signature = LogNormalization.normalizeSignature(signature);

        String svc = (serviceName == null) ? "" : serviceName.trim();
        return sha256Hex(svc + ":" + signature);
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

    public ErrorLogResponse convertToResponse(ErrorLog log, long impactedHostCount, boolean isNew, boolean isNewHost) {
        return ErrorLogResponse.builder()
                .logId(log.getId())
                .serviceName(log.getServiceName())
                .summary(log.getSummary())
                .errorCode(log.getErrorCode())
                .hostInfo(log.getHostName())
                .logHash(log.getLogHash())          // 슬랙 로그 확인용
                .repeatCount(log.getRepeatCount())  // 알림 조건 판별용
                .impactedHostCount(impactedHostCount)
                .isNew(isNew)
                .isNewHost(isNewHost)               // 확산 알림용
                .build();
    }
}
