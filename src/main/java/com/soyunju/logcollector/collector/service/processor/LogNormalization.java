package com.soyunju.logcollector.service.lc.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogNormalization {

    private LogNormalization() {
    }

    // =========================
    // 정규화 패턴
    // =========================
    private static final Pattern PATTERN_UUID =
            Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern PATTERN_IPV4 =
            Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");

    private static final Pattern PATTERN_IPV6 =
            Pattern.compile("\\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b");

    private static final Pattern PATTERN_TS_ISO =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?");

    private static final Pattern PATTERN_CONNECTORS_WITH_NUM =
            Pattern.compile("\\s+(at|on|near|value|id|is)\\s+\\d+", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_KV_ID =
            Pattern.compile("\\b(?i)(traceid|spanid|requestid|correlationid|txid|transactionid|sessionid|rid)\\s*[:=]\\s*([A-Za-z0-9._\\-]{6,})\\b");

    private static final Pattern PATTERN_URL =
            Pattern.compile("\\bhttps?://[^\\s]+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_PATH_WIN =
            Pattern.compile("\\b[A-Za-z]:\\\\[^\\s]+\\b");

    private static final Pattern PATTERN_PATH_UNIX =
            Pattern.compile("(/[^\\s]+)+");

    private static final Pattern PATTERN_EMAIL =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern PATTERN_MAC =
            Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");

    private static final Pattern PATTERN_JAVA_LINE =
            Pattern.compile("(\\.java:)(\\d+)\\b");

    private static final Pattern PATTERN_NUM_TOKEN =
            Pattern.compile("\\b\\d{2,}\\b");

    // ms 등을 못잡아서 숫자 앞뒤가 숫자가 아닌 경우만 잡기
  /* private static final Pattern PATTERN_NUM_TOKEN =
           Pattern.compile("(?<!\\d)\\d{2,}(?!\\d)"); */

    private static final Pattern PATTERN_NUM_WITH_UNIT =
            Pattern.compile("(?<!\\d)\\d{2,}(?=(ms|s|sec|secs|seconds)\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MULTI_NUM =
            Pattern.compile("(?:<NUM>\\s+)+<NUM>");

    private static final Pattern PATTERN_WS =
            Pattern.compile("\\s+");

    private static final Pattern P_ORA = Pattern.compile("\\bORA-(\\d{5})\\b");
    private static final Pattern P_SQLSTATE_CODE =
            Pattern.compile("\\bSQLSTATE\\s*[:=]?\\s*([0-9A-Z]{5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_HTTP_CODE =
            Pattern.compile("\\bHTTP\\s*([1-5]\\d\\d)\\b|\\bstatus\\s*[:=]\\s*([1-5]\\d\\d)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ERRNO_CODE =
            Pattern.compile("\\berrno\\s*[:=]?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_EXCEPTION =
            Pattern.compile("\\b([A-Za-z_$][A-Za-z0-9_$]*Exception)\\b");
    private static final Pattern PATTERN_BRACKET_KV_ID =
            Pattern.compile("\\[(?i)(traceid|spanid|requestid|correlationid|txid|transactionid|sessionid|rid)\\s*[:=]\\s*([^\\]]+)\\]");
    private static final Pattern PATTERN_POOL_CONN =
            Pattern.compile("\\b(?i)(pool|connid|connectionid)\\s*[:=]\\s*([A-Za-z0-9._\\-]+)\\b");


    public static String normalizeMessage(String message) {
        if (message == null) return "";

        String s = message;

        // 1. [] 로 감싼 equestId, traceId 등 제거
        s = PATTERN_BRACKET_KV_ID.matcher(s).replaceAll("[$1=<ID>]");
        // pool=HikariPool-1, connId=1001 같은 런타임 식별자 제거
        s = PATTERN_POOL_CONN.matcher(s).replaceAll("$1=<ID>");

        // 2. 의미 있는 코드 보호 (숫자 정규화 전에 선처리)
        s = replaceHttpStatusToken(s);
        s = P_ERRNO_CODE.matcher(s).replaceAll("ERRNO");
        s = P_SQLSTATE_CODE.matcher(s).replaceAll("SQLSTATE");

        // 3. 시간 / 위치 / 네트워크 / 경로 등 환경 노이즈 제거
        s = PATTERN_TS_ISO.matcher(s).replaceAll("<TS>");
        s = PATTERN_UUID.matcher(s).replaceAll("<UUID>");
        s = PATTERN_IPV4.matcher(s).replaceAll("<IP>");
        s = PATTERN_IPV6.matcher(s).replaceAll("<IP6>");
        s = PATTERN_MAC.matcher(s).replaceAll("<MAC>");
        s = PATTERN_EMAIL.matcher(s).replaceAll("<EMAIL>");

        // key=value 형태의 ID (traceId=xxx 등)
        s = PATTERN_KV_ID.matcher(s).replaceAll("$1=<ID>");

        // URL / 파일 경로 (Windows / Unix)
        s = PATTERN_URL.matcher(s).replaceAll("<URL>");
        s = PATTERN_PATH_WIN.matcher(s).replaceAll("<PATH>");
        s = PATTERN_PATH_UNIX.matcher(s).replaceAll("<PATH>");

        // 4. 숫자 정규화 (가장 마지막)
        s = PATTERN_CONNECTORS_WITH_NUM.matcher(s).replaceAll(" ");
        s = PATTERN_NUM_WITH_UNIT.matcher(s).replaceAll("<NUM>");
        s = PATTERN_NUM_TOKEN.matcher(s).replaceAll("<NUM>");

        // 5. 공백 등 후처리 정리
        s = PATTERN_MULTI_NUM.matcher(s).replaceAll("<NUM>");
        s = PATTERN_WS.matcher(s).replaceAll(" ").trim();

        return s;
    }


    public static String normalizeStackTop(String stackTrace, int maxLines) {
        if (stackTrace == null || stackTrace.isBlank() || maxLines <= 0) return "";

        String[] lines = stackTrace.split("\\R");
        List<String> top = new ArrayList<>(maxLines);

        for (String line : lines) {
            if (top.size() >= maxLines) break;

            String l = line.trim();
            if (l.isBlank()) continue;

            l = PATTERN_JAVA_LINE.matcher(l).replaceAll("$1<LINE>");
            l = normalizeMessage(l);

            if (!l.isBlank()) top.add(l);
        }
        return String.join(" / ", top);
    }

    public static String normalizeSignature(String signature) {
        if (signature == null) return "";
        String s = signature.toLowerCase();
        s = PATTERN_WS.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * error_code 생성
     * - 우선순위: ORA / SQLSTATE / HTTP / ERRNO / Exception / Category fallback
     */
    public static String generateErrorCode(String message, String stackTrace) {
        String msg = (message == null) ? "" : message;
        String st = (stackTrace == null) ? "" : stackTrace;
        String src = msg + "\n" + st;

        Matcher m = P_ORA.matcher(src);
        if (m.find()) return "ORA_" + m.group(1);

        m = P_SQLSTATE_CODE.matcher(src);
        if (m.find()) return "SQLSTATE_" + m.group(1);

        m = P_HTTP_CODE.matcher(src);
        if (m.find()) {
            String code = (m.group(1) != null) ? m.group(1) : m.group(2);
            return "HTTP_" + code;
        }

        m = P_ERRNO_CODE.matcher(src);
        if (m.find()) return "ERRNO_" + m.group(1);

        m = P_EXCEPTION.matcher(src);
        if (m.find()) return "EX_" + m.group(1);

        String upper = src.toUpperCase();
        if (upper.contains("SQL") || upper.contains("DATABASE")) return "DB_ERR";
        if (upper.contains("TIMEOUT") || upper.contains("CONNECTION") || upper.contains("REFUSED")) return "NET_ERR";
        if (upper.contains("NULLPOINTER") || upper.contains("OUTOFMEMORY") || upper.contains("ILLEGALSTATE"))
            return "SYS_ERR";
        return "GEN_ERR";
    }

    private static String replaceHttpStatusToken(String input) {
        Matcher m = P_HTTP_CODE.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            // 값 보존 정책으로 바꾸고 싶으면: "HTTP_" + code 로 변경
            String replacement = "HTTP_STATUS";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
