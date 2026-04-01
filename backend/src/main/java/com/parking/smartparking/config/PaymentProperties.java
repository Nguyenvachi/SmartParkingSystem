package com.parking.smartparking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    /**
     * Base URL where backend is reachable from the payment gateways. Default is
     * local dev.
     */
    private String backendBaseUrl = "http://localhost:8080";

    @Data
    public static class Momo {

        private boolean enabled = false;
        private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
        private String partnerCode;
        private String accessKey;
        private String secretKey;
        private String requestType = "captureWallet";
        private String lang = "vi";
        private String payWithMethod;
        private boolean allowUnsafeReturnSuccess = false;
        private String partnerName;
        private String storeId;
        private Boolean autoCapture = Boolean.TRUE;
        private String extraData = "";
    }

    @Data
    public static class Vnpay {

        private boolean enabled = false;
        private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        private String tmnCode;
        private String hashSecret;
        private String version = "2.1.0";
        private String command = "pay";
        private String locale = "vn";
        private String currCode = "VND";
        private String orderType = "other";
        /**
         * default minutes
         */
        private int expireMinutes = 15;
    }

    private Momo momo = new Momo();
    private Vnpay vnpay = new Vnpay();
}
