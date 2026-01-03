package com.soyunju.logcollector.service.processor;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.dto.ErrorLogResponse;
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

    public ErrorLogResponse convertToResponse(ErrorLog log) {
        return ErrorLogResponse.builder()
                .logId(log.getId())
                .serviceName(log.getServiceName())
                .summary(log.getSummary())
                .errorCode(log.getErrorCode())
                .hostInfo(log.getHostName())
                .build();
    }

    // 로그 해싱
    public String generateLogHash(String serviceName, String message) {
        try {
            String combined = serviceName + ":" + message;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            // 16진수 문자열로 변환
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
}
