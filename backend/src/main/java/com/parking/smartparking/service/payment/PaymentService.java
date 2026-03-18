package com.parking.smartparking.service.payment;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.smartparking.config.PaymentProperties;
import com.parking.smartparking.dto.response.PaymentCreateResponse;
import com.parking.smartparking.entity.PaymentOrder;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.PaymentOrderRepository;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.service.WalletService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentProperties paymentProperties;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.frontend.dashboard-path:/dashboard}")
    private String frontendDashboardPath;

    public record PaymentCallbackResult(String provider, String orderId, String status, String message) {

    }

    @Transactional
    public PaymentCreateResponse createMomoTopUp(String email, BigDecimal amount, String description) {
        PaymentProperties.Momo momo = paymentProperties.getMomo();
        ensureConfigured(momo.isEnabled(), "MoMo");
        ensureNotBlank(momo.getPartnerCode(), "MoMo partnerCode");
        ensureNotBlank(momo.getAccessKey(), "MoMo accessKey");
        ensureNotBlank(momo.getSecretKey(), "MoMo secretKey");

        BigDecimal normalized = normalizeVndAmount(amount);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));

        String orderId = newOrderId("SPMOMO");
        String requestId = orderId;
        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.builder()
                .user(user)
                .provider(PaymentOrder.Provider.MOMO)
                .purpose(PaymentOrder.Purpose.WALLET_TOPUP)
                .status(PaymentOrder.Status.PENDING)
                .orderId(orderId)
                .requestId(requestId)
                .amount(normalized)
                .description(description)
                .build());

        String redirectUrl = paymentProperties.getBackendBaseUrl() + "/api/payments/callback/momo/return";
        String ipnUrl = paymentProperties.getBackendBaseUrl() + "/api/payments/callback/momo/ipn";
        String orderInfo = "Top up Smart Parking | " + user.getEmail();
        String extraData = "";

        String rawSignature = "accessKey=" + momo.getAccessKey()
                + "&amount=" + asVndIntegerString(order.getAmount())
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + momo.getPartnerCode()
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + momo.getRequestType();

        String signature = CryptoUtils.hmacSha256Hex(momo.getSecretKey(), rawSignature);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", momo.getPartnerCode());
        payload.put("accessKey", momo.getAccessKey());
        payload.put("requestId", requestId);
        payload.put("amount", asVndIntegerString(order.getAmount()));
        payload.put("orderId", orderId);
        payload.put("orderInfo", orderInfo);
        payload.put("redirectUrl", redirectUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("extraData", extraData);
        payload.put("requestType", momo.getRequestType());
        payload.put("lang", momo.getLang());
        payload.put("signature", signature);

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder(URI.create(momo.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode json = objectMapper.readTree(resp.body());
            int resultCode = json.path("resultCode").asInt(-1);
            String message = json.path("message").asText("");
            String payUrl = json.path("payUrl").asText("");

            if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resultCode != 0 || payUrl == null || payUrl.isBlank()) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode(String.valueOf(resultCode));
                order.setGatewayMessage(message);
                paymentOrderRepository.save(order);
                throw new RuntimeException("MoMo tạo thanh toán thất bại: " + (message.isBlank() ? ("code=" + resultCode) : message));
            }

            order.setGatewayCode(String.valueOf(resultCode));
            order.setGatewayMessage(message);
            paymentOrderRepository.save(order);

            return PaymentCreateResponse.builder()
                    .provider("MOMO")
                    .orderId(orderId)
                    .paymentUrl(payUrl)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thanh toán MoMo (IO): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Không thể tạo thanh toán MoMo (interrupted): " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo thanh toán MoMo: " + e.getMessage(), e);
        }
    }

    @Transactional
    public PaymentCreateResponse createVnpayTopUp(String email, BigDecimal amount, String description, HttpServletRequest request) {
        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        ensureConfigured(vnpay.isEnabled(), "VNPay");
        ensureNotBlank(vnpay.getTmnCode(), "VNPay tmnCode");
        ensureNotBlank(vnpay.getHashSecret(), "VNPay hashSecret");

        BigDecimal normalized = normalizeVndAmount(amount);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));

        String orderId = newOrderId("SPVNP");
        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.builder()
                .user(user)
                .provider(PaymentOrder.Provider.VNPAY)
                .purpose(PaymentOrder.Purpose.WALLET_TOPUP)
                .status(PaymentOrder.Status.PENDING)
                .orderId(orderId)
                .amount(normalized)
                .description(description)
                .build());

        String returnUrl = paymentProperties.getBackendBaseUrl() + "/api/payments/callback/vnpay/return";
        String ipAddr = resolveClientIp(request);
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        LocalDateTime expire = now.plusMinutes(Math.max(1, vnpay.getExpireMinutes()));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", vnpay.getVersion());
        params.put("vnp_Command", vnpay.getCommand());
        params.put("vnp_TmnCode", vnpay.getTmnCode());
        params.put("vnp_Amount", asVnpAmount(order.getAmount()));
        params.put("vnp_CurrCode", vnpay.getCurrCode());
        params.put("vnp_TxnRef", orderId);
        // VNPay recommends: no Vietnamese diacritics and avoid special characters.
        params.put("vnp_OrderInfo", "Nap tien vi SmartParking. UserId " + user.getId());
        params.put("vnp_OrderType", vnpay.getOrderType());
        params.put("vnp_Locale", vnpay.getLocale());
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_CreateDate", now.format(VNPAY_TIME_FMT));
        params.put("vnp_ExpireDate", expire.format(VNPAY_TIME_FMT));

        String hashData = params.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> UrlUtils.encode(e.getKey()) + "=" + UrlUtils.encode(e.getValue()))
                .collect(Collectors.joining("&"));

        String secureHash = CryptoUtils.hmacSha512Hex(vnpay.getHashSecret(), hashData);

        String query = params.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> UrlUtils.encode(e.getKey()) + "=" + UrlUtils.encode(e.getValue()))
                .collect(Collectors.joining("&"));

        String paymentUrl = vnpay.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;

        return PaymentCreateResponse.builder()
                .provider("VNPAY")
                .orderId(orderId)
                .paymentUrl(paymentUrl)
                .build();
    }

    @Transactional
    public PaymentCallbackResult handleMomoCallback(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return new PaymentCallbackResult("MOMO", "", "failed", "Missing fields");
        }

        String orderId = trimToNull(fields.get("orderId"));
        String resultCode = trimToNull(fields.get("resultCode"));
        String amountStr = trimToNull(fields.get("amount"));
        String message = trimToNull(fields.get("message"));

        if (orderId == null) {
            return new PaymentCallbackResult("MOMO", "", "failed", "Missing orderId");
        }

        Optional<PaymentOrder> opt = paymentOrderRepository.findByOrderIdForUpdate(orderId);
        if (opt.isEmpty()) {
            return new PaymentCallbackResult("MOMO", orderId, "failed", "Order not found");
        }

        PaymentOrder order = opt.get();
        if (order.getStatus() == PaymentOrder.Status.SUCCESS) {
            return new PaymentCallbackResult("MOMO", orderId, "success", message);
        }

        BigDecimal callbackAmount = safeParseBigDecimal(amountStr);
        if (callbackAmount != null && order.getAmount() != null && callbackAmount.compareTo(order.getAmount().setScale(0, RoundingMode.DOWN)) != 0) {
            order.setStatus(PaymentOrder.Status.FAILED);
            order.setGatewayCode("AMOUNT_MISMATCH");
            order.setGatewayMessage("Amount mismatch");
            paymentOrderRepository.save(order);
            return new PaymentCallbackResult("MOMO", orderId, "failed", "Amount mismatch");
        }

        if (!"0".equals(resultCode)) {
            order.setStatus(PaymentOrder.Status.FAILED);
            order.setGatewayCode(resultCode);
            order.setGatewayMessage(message);
            paymentOrderRepository.save(order);
            return new PaymentCallbackResult("MOMO", orderId, "failed", message);
        }

        // Signature verification (required when secret configured)
        PaymentProperties.Momo momo = paymentProperties.getMomo();
        String signature = trimToNull(fields.get("signature"));
        if (momo.getSecretKey() != null && !momo.getSecretKey().isBlank()) {
            if (signature == null) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_MISSING");
                order.setGatewayMessage("Missing signature");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("MOMO", orderId, "failed", "Missing signature");
            }
            try {
                String raw = buildMomoCallbackRaw(fields);
                String expected = CryptoUtils.hmacSha256Hex(momo.getSecretKey(), raw);
                if (!expected.equalsIgnoreCase(signature)) {
                    order.setStatus(PaymentOrder.Status.FAILED);
                    order.setGatewayCode("SIGNATURE_INVALID");
                    order.setGatewayMessage("Invalid signature");
                    paymentOrderRepository.save(order);
                    return new PaymentCallbackResult("MOMO", orderId, "failed", "Invalid signature");
                }
            } catch (Exception e) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_VERIFY_ERROR");
                order.setGatewayMessage("Signature verify error");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("MOMO", orderId, "failed", "Signature verify error");
            }
        }

        order.setStatus(PaymentOrder.Status.SUCCESS);
        order.setGatewayCode(resultCode);
        order.setGatewayMessage(message);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        walletService.creditTopUpFromGateway(
                order.getUser().getId(),
                order.getAmount(),
                "Nạp ví qua MoMo | orderId=" + order.getOrderId());

        return new PaymentCallbackResult("MOMO", orderId, "success", message);
    }

    @Transactional
    public PaymentCallbackResult handleVnpayReturn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new PaymentCallbackResult("VNPAY", "", "failed", "Missing params");
        }

        String txnRef = trimToNull(params.get("vnp_TxnRef"));
        if (txnRef == null) {
            return new PaymentCallbackResult("VNPAY", "", "failed", "Missing vnp_TxnRef");
        }

        Optional<PaymentOrder> opt = paymentOrderRepository.findByOrderIdForUpdate(txnRef);
        if (opt.isEmpty()) {
            return new PaymentCallbackResult("VNPAY", txnRef, "failed", "Order not found");
        }

        PaymentOrder order = opt.get();
        if (order.getStatus() == PaymentOrder.Status.SUCCESS) {
            return new PaymentCallbackResult("VNPAY", txnRef, "success", "OK");
        }

        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        String receivedHash = trimToNull(params.get("vnp_SecureHash"));
        if (vnpay.getHashSecret() != null && !vnpay.getHashSecret().isBlank()) {
            if (receivedHash == null) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_MISSING");
                order.setGatewayMessage("Missing secure hash");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("VNPAY", txnRef, "failed", "Missing secure hash");
            }
            Map<String, String> filtered = params.entrySet().stream()
                    .filter(e -> e.getKey() != null)
                    .filter(e -> !"vnp_SecureHash".equalsIgnoreCase(e.getKey()))
                    .filter(e -> !"vnp_SecureHashType".equalsIgnoreCase(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Objects.toString(e.getValue(), ""), (a, b) -> a, LinkedHashMap::new));

            String hashData = filtered.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(e -> UrlUtils.encode(e.getKey()) + "=" + UrlUtils.encode(e.getValue()))
                    .collect(Collectors.joining("&"));

            String expected = CryptoUtils.hmacSha512Hex(vnpay.getHashSecret(), hashData);
            if (!expected.equalsIgnoreCase(receivedHash)) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_INVALID");
                order.setGatewayMessage("Invalid secure hash");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("VNPAY", txnRef, "failed", "Invalid secure hash");
            }
        }

        String responseCode = trimToNull(params.get("vnp_ResponseCode"));
        String transStatus = trimToNull(params.get("vnp_TransactionStatus"));
        String amountStr = trimToNull(params.get("vnp_Amount"));

        BigDecimal paidAmount = safeParseVnpAmount(amountStr);
        if (paidAmount != null && order.getAmount() != null && paidAmount.compareTo(order.getAmount().setScale(0, RoundingMode.DOWN)) != 0) {
            order.setStatus(PaymentOrder.Status.FAILED);
            order.setGatewayCode("AMOUNT_MISMATCH");
            order.setGatewayMessage("Amount mismatch");
            paymentOrderRepository.save(order);
            return new PaymentCallbackResult("VNPAY", txnRef, "failed", "Amount mismatch");
        }

        boolean success = "00".equals(responseCode) && (transStatus == null || "00".equals(transStatus));
        if (!success) {
            order.setStatus(PaymentOrder.Status.FAILED);
            order.setGatewayCode(responseCode != null ? responseCode : "FAILED");
            order.setGatewayMessage("VNPay not successful");
            paymentOrderRepository.save(order);
            return new PaymentCallbackResult("VNPAY", txnRef, "failed", "VNPay not successful");
        }

        order.setStatus(PaymentOrder.Status.SUCCESS);
        order.setGatewayCode(responseCode);
        order.setGatewayMessage("OK");
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        walletService.creditTopUpFromGateway(
                order.getUser().getId(),
                order.getAmount(),
                "Nạp ví qua VNPay | orderId=" + order.getOrderId());

        return new PaymentCallbackResult("VNPAY", txnRef, "success", "OK");
    }

    public String buildFrontendRedirectUrl(PaymentCallbackResult result) {
        String base = normalizeUrl(frontendBaseUrl, frontendDashboardPath);
        String msg = result.message() != null ? result.message() : "";

        return base
                + (base.contains("?") ? "&" : "?")
                + "payment=" + UrlUtils.encode(result.status())
                + "&provider=" + UrlUtils.encode(result.provider())
                + "&orderId=" + UrlUtils.encode(result.orderId() != null ? result.orderId() : "")
                + "&message=" + UrlUtils.encode(msg);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String newOrderId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void ensureConfigured(boolean enabled, String provider) {
        if (!enabled) {
            throw new RuntimeException(provider + " chưa được bật trong cấu hình (app.payment." + provider.toLowerCase() + ".enabled).");
        }
    }

    private static void ensureNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình: " + name);
        }
    }

    private static BigDecimal normalizeVndAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new RuntimeException("Số tiền không hợp lệ.");
        }
        // VND should be integer
        try {
            return amount.stripTrailingZeros().setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new RuntimeException("Số tiền VND phải là số nguyên.");
        }
    }

    private static String asVndIntegerString(BigDecimal amount) {
        BigDecimal n = normalizeVndAmount(amount);
        return n.toPlainString();
    }

    private static String asVnpAmount(BigDecimal amount) {
        // VNPay amount in VND * 100
        BigDecimal n = normalizeVndAmount(amount);
        return n.multiply(BigDecimal.valueOf(100L)).toPlainString();
    }

    private static BigDecimal safeParseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal safeParseVnpAmount(String value) {
        BigDecimal raw = safeParseBigDecimal(value);
        if (raw == null) {
            return null;
        }
        return raw.divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isBlank() ? null : t;
    }

    /**
     * Try to build MoMo callback raw string per common docs. If required fields
     * are missing, an exception may be thrown.
     */
    private static String buildMomoCallbackRaw(Map<String, String> f) {
        String accessKey = Objects.toString(f.get("accessKey"), "");
        String amount = Objects.toString(f.get("amount"), "");
        String extraData = Objects.toString(f.get("extraData"), "");
        String message = Objects.toString(f.get("message"), "");
        String orderId = Objects.toString(f.get("orderId"), "");
        String orderInfo = Objects.toString(f.get("orderInfo"), "");
        String orderType = Objects.toString(f.get("orderType"), "");
        String partnerCode = Objects.toString(f.get("partnerCode"), "");
        String payType = Objects.toString(f.get("payType"), "");
        String requestId = Objects.toString(f.get("requestId"), "");
        String responseTime = Objects.toString(f.get("responseTime"), "");
        String resultCode = Objects.toString(f.get("resultCode"), "");
        String transId = Objects.toString(f.get("transId"), "");

        return "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + partnerCode
                + "&payType=" + payType
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;
    }

    private static String normalizeUrl(String base, String path) {
        String b = base != null ? base.trim() : "";
        String p = path != null ? path.trim() : "";
        if (b.endsWith("/") && p.startsWith("/")) {
            return b.substring(0, b.length() - 1) + p;
        }
        if (!b.endsWith("/") && !p.isBlank() && !p.startsWith("/")) {
            return b + "/" + p;
        }
        return b + p;
    }
}
