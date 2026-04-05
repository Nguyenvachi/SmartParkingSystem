package com.parking.smartparking.controller;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.parking.smartparking.dto.request.PaymentTopUpCreateRequest;
import com.parking.smartparking.dto.response.PaymentCreateResponse;
import com.parking.smartparking.dto.response.PaymentOrderStatusResponse;
import com.parking.smartparking.service.payment.PaymentService;
import com.parking.smartparking.service.payment.PaymentService.PaymentCallbackResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/topup/momo")
    public ResponseEntity<PaymentCreateResponse> createMomoTopUp(
            @Valid @RequestBody PaymentTopUpCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest) {
        String email = authentication.getName();
        return ResponseEntity.ok(paymentService.createMomoTopUp(email, request.getAmount(), request.getDescription(), httpServletRequest));
    }

    @PostMapping("/topup/vnpay")
    public ResponseEntity<PaymentCreateResponse> createVnpayTopUp(
            @Valid @RequestBody PaymentTopUpCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest) {
        String email = authentication.getName();
        return ResponseEntity.ok(paymentService.createVnpayTopUp(email, request.getAmount(), request.getDescription(), httpServletRequest));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentOrderStatusResponse> getOrderStatus(
            @PathVariable String orderId,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(paymentService.getOrderStatus(email, orderId));
    }

    // =====================
    // MoMo callbacks
    // =====================
    @GetMapping("/callback/momo/return")
    public void momoReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = flattenParams(request);
        log.info("[PAYMENT] MoMo return callback | orderId={} | resultCode={} | from={} | xfProto={} | xfHost={}",
                params.get("orderId"), params.get("resultCode"), request.getRemoteAddr(), request.getHeader("X-Forwarded-Proto"), request.getHeader("X-Forwarded-Host"));
        PaymentCallbackResult result = paymentService.handleMomoCallback(params, true);
        response.sendRedirect(paymentService.buildFrontendRedirectUrl(result, request));
    }

    @PostMapping("/callback/momo/ipn")
    public ResponseEntity<Map<String, Object>> momoIpn(@RequestBody(required = false) JsonNode payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (payload != null && payload.isObject()) {
            Iterator<String> it = payload.fieldNames();
            while (it.hasNext()) {
                String key = it.next();
                JsonNode value = payload.get(key);
                fields.put(key, value == null || value.isNull() ? null : value.asText());
            }
        }

        log.info("[PAYMENT] MoMo IPN callback | orderId={} | resultCode={}", fields.get("orderId"), fields.get("resultCode"));
        paymentService.handleMomoCallback(fields, false);
        // MoMo expects a 200 response; body format varies by API version.
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // =====================
    // VNPay callbacks
    // =====================
    @GetMapping("/callback/vnpay/return")
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = flattenParams(request);
        log.info("[PAYMENT] VNPay return callback | vnp_TxnRef={} | vnp_ResponseCode={} | from={} | xfProto={} | xfHost={}",
                params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"), request.getRemoteAddr(), request.getHeader("X-Forwarded-Proto"), request.getHeader("X-Forwarded-Host"));
        PaymentCallbackResult result = paymentService.handleVnpayReturn(params);
        response.sendRedirect(paymentService.buildFrontendRedirectUrl(result, request));
    }

    @GetMapping("/callback/vnpay/ipn")
    public ResponseEntity<Map<String, Object>> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = flattenParams(request);
        log.info("[PAYMENT] VNPay IPN callback | vnp_TxnRef={} | vnp_ResponseCode={}", params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));
        paymentService.handleVnpayReturn(params);
        return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "OK"));
    }

    private static Map<String, String> flattenParams(HttpServletRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        if (request == null) {
            return out;
        }
        request.getParameterMap().forEach((key, values) -> {
            if (values == null || values.length == 0) {
                out.put(key, null);
                return;
            }
            out.put(key, values[0]);
        });
        return out;
    }
}
