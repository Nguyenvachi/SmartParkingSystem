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

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/topup/momo")
    public ResponseEntity<PaymentCreateResponse> createMomoTopUp(
            @Valid @RequestBody PaymentTopUpCreateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(paymentService.createMomoTopUp(email, request.getAmount(), request.getDescription()));
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
        PaymentCallbackResult result = paymentService.handleMomoCallback(flattenParams(request), true);
        response.sendRedirect(paymentService.buildFrontendRedirectUrl(result));
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

        paymentService.handleMomoCallback(fields, false);
        // MoMo expects a 200 response; body format varies by API version.
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // =====================
    // VNPay callbacks
    // =====================
    @GetMapping("/callback/vnpay/return")
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PaymentCallbackResult result = paymentService.handleVnpayReturn(flattenParams(request));
        response.sendRedirect(paymentService.buildFrontendRedirectUrl(result));
    }

    @GetMapping("/callback/vnpay/ipn")
    public ResponseEntity<Map<String, Object>> vnpayIpn(HttpServletRequest request) {
        paymentService.handleVnpayReturn(flattenParams(request));
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
