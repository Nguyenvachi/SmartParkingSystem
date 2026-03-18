package com.parking.smartparking.service.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String encode(String value) {
        if (value == null) {
            return "";
        }
        // VNPay sandbox sample uses standard application/x-www-form-urlencoded encoding (space => '+').
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
