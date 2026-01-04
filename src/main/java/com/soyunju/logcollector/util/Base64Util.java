package com.soyunju.logcollector.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 encoding / decoding utility class
 */
public final class Base64Util {

    private Base64Util() {
        throw new AssertionError("No instances allowed");
    }

    /* =====================
     * Standard Base64
     * ===================== */

    public static String encode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String base64) {
        if (base64 == null) {
            return null;
        }
        return Base64.getDecoder().decode(base64);
    }

    public static String encodeString(String plainText) {
        return encodeString(plainText, StandardCharsets.UTF_8);
    }

    public static String encodeString(String plainText, Charset charset) {
        if (plainText == null) {
            return null;
        }
        return encode(plainText.getBytes(charset));
    }

    public static String decodeToString(String base64) {
        return decodeToString(base64, StandardCharsets.UTF_8);
    }

    public static String decodeToString(String base64, Charset charset) {
        if (base64 == null) {
            return null;
        }
        return new String(decode(base64), charset);
    }

    /* =====================
     * URL-safe Base64
     * (JWT, URL parameter 등)
     * ===================== */

    public static String encodeUrlSafe(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public static byte[] decodeUrlSafe(String base64UrlSafe) {
        if (base64UrlSafe == null) {
            return null;
        }
        return Base64.getUrlDecoder().decode(base64UrlSafe);
    }
}
