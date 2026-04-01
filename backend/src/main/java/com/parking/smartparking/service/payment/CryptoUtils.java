package com.parking.smartparking.service.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {

    private CryptoUtils() {
    }

    public static String hmacSha256Hex(String secretKey, String data) {
        return hmacHex("HmacSHA256", secretKey, data);
    }

    public static String hmacSha512Hex(String secretKey, String data) {
        return hmacHex("HmacSHA512", secretKey, data);
    }

    private static String hmacHex(String algorithm, String secretKey, String data) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Missing secret key");
        }
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] raw = mac.doFinal((data != null ? data : "").getBytes(StandardCharsets.UTF_8));
            return toHexLower(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC failure: " + algorithm, e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
