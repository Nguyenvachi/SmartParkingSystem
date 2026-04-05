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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.parking.smartparking.dto.response.PaymentOrderStatusResponse;
import com.parking.smartparking.entity.PaymentOrder;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.PaymentOrderRepository;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.service.WalletService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class PaymentService {

    private static final String DEFAULT_BACKEND_BASE_URL = "http://localhost:8080";

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

    @Value("${app.wallet.minimum-topup:10000}")
    private BigDecimal minimumTopUp;

    public record PaymentCallbackResult(String provider, String orderId, String status, String message) {

    }

    @Transactional
    public PaymentCreateResponse createMomoTopUp(String email, BigDecimal amount, String description, HttpServletRequest request) {
        PaymentProperties.Momo momo = paymentProperties.getMomo();
        ensureConfigured(momo.isEnabled(), "MoMo");
        String partnerCode = requireTrimmed(momo.getPartnerCode(), "MoMo partnerCode");
        String accessKey = requireTrimmed(momo.getAccessKey(), "MoMo accessKey");
        String secretKey = requireTrimmed(momo.getSecretKey(), "MoMo secretKey");

        BigDecimal normalized = normalizeVndAmount(amount);
        if (minimumTopUp != null && normalized.compareTo(minimumTopUp) < 0) {
            throw new RuntimeException("Số tiền nạp tối thiểu là " + minimumTopUp.toPlainString() + " VND.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));

        LocalDateTime antiSpamSince = LocalDateTime.now().minusMinutes(2);
        Optional<PaymentOrder> recentPending = paymentOrderRepository.findRecentByUserAndProviderAndPurposeAndStatus(
                user.getId(),
                PaymentOrder.Provider.MOMO,
                PaymentOrder.Purpose.WALLET_TOPUP,
                PaymentOrder.Status.PENDING,
                antiSpamSince);
        if (recentPending.isPresent()) {
            throw new RuntimeException("Bạn đang có yêu cầu nạp MoMo chưa hoàn tất. Vui lòng hoàn tất giao dịch trước khi tạo yêu cầu mới.");
        }

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

        String callbackBaseUrl = resolveCallbackBaseUrl(request);
        String redirectUrl = callbackBaseUrl + "/api/payments/callback/momo/return";
        String ipnUrl = callbackBaseUrl + "/api/payments/callback/momo/ipn";
        log.info("[PAYMENT] MoMo create | orderId={} | callbackBaseUrl={} | redirectUrl={} | ipnUrl={}", orderId, callbackBaseUrl, redirectUrl, ipnUrl);
        String orderInfo = "Top up SmartParking user " + user.getId();
        String extraData = Objects.toString(momo.getExtraData(), "");

        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + asVndIntegerString(order.getAmount())
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + momo.getRequestType();

        String signature = CryptoUtils.hmacSha256Hex(secretKey, rawSignature);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", partnerCode);
        payload.put("accessKey", accessKey);
        payload.put("requestId", requestId);
        payload.put("amount", asVndIntegerString(order.getAmount()));
        payload.put("orderId", orderId);
        payload.put("orderInfo", orderInfo);
        payload.put("redirectUrl", redirectUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("extraData", extraData);
        payload.put("requestType", momo.getRequestType());
        if (momo.getPayWithMethod() != null && !momo.getPayWithMethod().isBlank()) {
            payload.put("payWithMethod", momo.getPayWithMethod().trim());
        }
        if (momo.getAutoCapture() != null) {
            payload.put("autoCapture", momo.getAutoCapture());
        }
        if (momo.getPartnerName() != null && !momo.getPartnerName().isBlank()) {
            payload.put("partnerName", momo.getPartnerName().trim());
        }
        if (momo.getStoreId() != null && !momo.getStoreId().isBlank()) {
            payload.put("storeId", momo.getStoreId().trim());
        }
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

            if (log.isDebugEnabled()) {
                log.debug("MoMo create response | httpStatus={} | resultCode={} | message={}", resp.statusCode(), resultCode, message);
            }

            if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resultCode != 0 || payUrl == null || payUrl.isBlank()) {
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode(String.valueOf(resultCode));
                order.setGatewayMessage(message);
                paymentOrderRepository.save(order);
                String detail = (message == null || message.isBlank()) ? ("code=" + resultCode) : message;
                if (resultCode == 13 || (message != null && message.toLowerCase().contains("không hoạt động"))) {
                    detail = enrichMomoConfigError(detail, redirectUrl, ipnUrl);
                }
                throw new RuntimeException("MoMo tạo thanh toán thất bại (http=" + resp.statusCode() + ", code=" + resultCode + "): " + detail);
            }

            order.setGatewayCode(String.valueOf(resultCode));
            // resultCode=0 here only means create-order API succeeded, not payment success.
            order.setStatus(PaymentOrder.Status.PENDING);
            order.setGatewayMessage("Đã tạo link thanh toán, chờ người dùng xác nhận trên cổng MoMo");
            paymentOrderRepository.save(order);

            return PaymentCreateResponse.builder()
                    .provider("MOMO")
                    .orderId(orderId)
                    .paymentUrl(payUrl)
                    .build();
        } catch (IOException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new RuntimeException("Không thể tạo thanh toán MoMo (IO): " + detail, e);
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
        if (minimumTopUp != null && normalized.compareTo(minimumTopUp) < 0) {
            throw new RuntimeException("Số tiền nạp tối thiểu là " + minimumTopUp.toPlainString() + " VND.");
        }

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

        String callbackBaseUrl = resolveCallbackBaseUrl(request);
        String returnUrl = callbackBaseUrl + "/api/payments/callback/vnpay/return";
        log.info("[PAYMENT] VNPay create | orderId={} | callbackBaseUrl={} | returnUrl={}", orderId, callbackBaseUrl, returnUrl);
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
        return handleMomoCallback(fields, false);
    }

    @Transactional
    public PaymentCallbackResult handleMomoCallback(Map<String, String> fields, boolean fromReturn) {
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
            if ("0".equals(resultCode) && isFallbackReturnMessage(order.getGatewayMessage())) {
                String normalized = normalizeMomoSuccessMessage(message);
                order.setGatewayCode("0");
                order.setGatewayMessage(normalized);
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("MOMO", orderId, "success", normalized);
            }
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
                if (fromReturn && momo.isAllowUnsafeReturnSuccess()) {
                    return markMomoSuccess(order, resultCode, "Missing signature (accepted on return for local test)");
                }
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_MISSING");
                order.setGatewayMessage("Missing signature");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("MOMO", orderId, "failed", "Missing signature");
            }
            try {
                if (!verifyMomoCallbackSignature(fields, momo.getSecretKey(), signature)) {
                    if (fromReturn && momo.isAllowUnsafeReturnSuccess()) {
                        return markMomoSuccess(order, resultCode, "Invalid signature (accepted on return for local test)");
                    }
                    order.setStatus(PaymentOrder.Status.FAILED);
                    order.setGatewayCode("SIGNATURE_INVALID");
                    order.setGatewayMessage("Invalid signature");
                    paymentOrderRepository.save(order);
                    return new PaymentCallbackResult("MOMO", orderId, "failed", "Invalid signature");
                }
            } catch (Exception e) {
                if (fromReturn && momo.isAllowUnsafeReturnSuccess()) {
                    return markMomoSuccess(order, resultCode, "Signature verify error (accepted on return for local test)");
                }
                order.setStatus(PaymentOrder.Status.FAILED);
                order.setGatewayCode("SIGNATURE_VERIFY_ERROR");
                order.setGatewayMessage("Signature verify error");
                paymentOrderRepository.save(order);
                return new PaymentCallbackResult("MOMO", orderId, "failed", "Signature verify error");
            }
        }

        return markMomoSuccess(order, resultCode, message);
    }

    private PaymentCallbackResult markMomoSuccess(PaymentOrder order, String resultCode, String message) {
        String normalizedMessage = normalizeMomoSuccessMessage(message);
        order.setStatus(PaymentOrder.Status.SUCCESS);
        order.setGatewayCode(resultCode);
        order.setGatewayMessage(normalizedMessage);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        walletService.creditTopUpFromGateway(
                order.getUser().getId(),
                order.getAmount(),
                "Nạp ví qua MoMo | orderId=" + order.getOrderId());

        return new PaymentCallbackResult("MOMO", order.getOrderId(), "success", normalizedMessage);
    }

    private static String normalizeMomoSuccessMessage(String message) {
        String m = trimToNull(message);
        if (m == null) {
            return "Thành công.";
        }
        String lower = m.toLowerCase();
        if (lower.contains("accepted on return") || lower.contains("invalid signature") || lower.contains("signature verify")) {
            return "Thành công.";
        }
        return m;
    }

    private static boolean isFallbackReturnMessage(String message) {
        String m = trimToNull(message);
        if (m == null) {
            return false;
        }
        String lower = m.toLowerCase();
        return lower.contains("accepted on return") || lower.contains("invalid signature") || lower.contains("signature verify");
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

    public String buildFrontendRedirectUrl(PaymentCallbackResult result, HttpServletRequest request) {
        String base = normalizeUrl(rewriteLocalhostToRequestHost(frontendBaseUrl, request), frontendDashboardPath);
        String msg = result.message() != null ? result.message() : "";

        return base
                + (base.contains("?") ? "&" : "?")
                + "payment=" + UrlUtils.encode(result.status())
                + "&provider=" + UrlUtils.encode(result.provider())
                + "&orderId=" + UrlUtils.encode(result.orderId() != null ? result.orderId() : "")
                + "&message=" + UrlUtils.encode(msg);
    }

    private String resolveCallbackBaseUrl(HttpServletRequest request) {
        String configured = normalizeCallbackBaseUrl(paymentProperties.getBackendBaseUrl());
        if (configured != null && !isLocalhostLikeBaseUrl(configured)) {
            return configured;
        }

        String derived = normalizeCallbackBaseUrl(deriveBaseUrlFromRequest(request));
        if (derived != null && !derived.isBlank() && !isLocalhostLikeBaseUrl(derived)) {
            return derived;
        }

        // Local dev fallback: allow localhost when we cannot derive a better public URL.
        return configured != null ? configured : DEFAULT_BACKEND_BASE_URL;
    }

    private static String normalizeCallbackBaseUrl(String url) {
        String u = normalizeBaseUrl(trimToNull(url));
        if (u == null) {
            return null;
        }
        // Ensure we only keep scheme://host[:port] (some users mistakenly set .../api)
        try {
            URI uri = URI.create(u);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return u;
            }
            URI rebuilt = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    null,
                    null,
                    null);
            return normalizeBaseUrl(rebuilt.toString());
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            return u;
        }
    }

    private static boolean isLocalhostLikeBaseUrl(String baseUrl) {
        String u = normalizeBaseUrl(trimToNull(baseUrl));
        if (u == null) {
            return true;
        }
        try {
            URI uri = URI.create(u);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                // Best-effort fallback
                String authority = uri.getAuthority();
                if (authority != null) {
                    String auth = authority;
                    int at = auth.lastIndexOf('@');
                    if (at >= 0) {
                        auth = auth.substring(at + 1);
                    }
                    int colon = auth.indexOf(':');
                    host = (colon >= 0) ? auth.substring(0, colon) : auth;
                }
            }
            if (host == null || host.isBlank()) {
                return false;
            }
            String h = host.trim().toLowerCase();
            return "localhost".equals(h)
                    || "127.0.0.1".equals(h)
                    || "0.0.0.0".equals(h)
                    || "::1".equals(h);
        } catch (IllegalArgumentException e) {
            String lower = u.toLowerCase();
            return lower.contains("localhost") || lower.contains("127.0.0.1");
        }
    }

    private static String deriveBaseUrlFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String proto = firstHeaderValue(request, "X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }

        String host = firstHeaderValue(request, "X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = firstHeaderValue(request, "Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(proto) && port == 80)
                    || ("https".equalsIgnoreCase(proto) && port == 443);
            if (!defaultPort && port > 0) {
                host = host + ":" + port;
            }
        }

        if (host == null || host.isBlank()) {
            return null;
        }
        // Handle multiple forwarded hosts: "a,b" -> first
        if (host.contains(",")) {
            host = host.split(",")[0].trim();
        }

        return proto + "://" + host;
    }

    private static String rewriteLocalhostToRequestHost(String url, HttpServletRequest request) {
        String u = trimToNull(url);
        if (u == null) {
            return url;
        }
        if (!(u.startsWith("http://localhost") || u.startsWith("http://127.0.0.1")
                || u.startsWith("https://localhost") || u.startsWith("https://127.0.0.1"))) {
            return url;
        }

        String reqBase = deriveBaseUrlFromRequest(request);
        if (reqBase == null || reqBase.isBlank()) {
            return url;
        }

        try {
            URI original = URI.create(u);
            URI requestBase = URI.create(reqBase);

            // If frontend base URL is localhost, prefer the request's public scheme/host.
            // Avoid leaking local dev ports (e.g. :3000) into production redirects.
            String scheme = requestBase.getScheme() != null ? requestBase.getScheme() : original.getScheme();
            String host = requestBase.getHost();
            int port = requestBase.getPort();

            if (host == null || host.isBlank()) {
                return url;
            }

            URI rebuilt = new URI(
                    scheme,
                    original.getUserInfo(),
                    host,
                    port,
                    original.getPath(),
                    original.getQuery(),
                    original.getFragment());
            return rebuilt.toString();
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            return url;
        }
    }

    private static String firstHeaderValue(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeBaseUrl(String url) {
        String u = trimToNull(url);
        if (u == null) {
            return null;
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String normalizeUrl(String baseUrl, String path) {
        String base = normalizeBaseUrl(baseUrl);
        String p = path == null ? "" : path.trim();
        if (base == null) {
            base = "";
        }
        if (p.isEmpty()) {
            return base;
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }

    @Transactional(readOnly = true)
    public PaymentOrderStatusResponse getOrderStatus(String email, String orderId) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Bạn chưa đăng nhập.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new RuntimeException("Thiếu orderId.");
        }

        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId.trim())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch."));

        String ownerEmail = order.getUser() != null ? order.getUser().getEmail() : null;
        if (ownerEmail == null || !ownerEmail.equalsIgnoreCase(email.trim())) {
            throw new RuntimeException("Không có quyền xem giao dịch này.");
        }

        return PaymentOrderStatusResponse.builder()
                .provider(order.getProvider() != null ? order.getProvider().name() : null)
                .orderId(order.getOrderId())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .amount(order.getAmount())
                .message(order.getGatewayMessage())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .build();
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

    private static String requireTrimmed(String value, String name) {
        ensureNotBlank(value, name);
        return value.trim();
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

    /**
     * Some MoMo flows (especially web-bank/ATM) return callback payload
     * variants. Verify against several known canonical forms to reduce false
     * negatives.
     */
    private static boolean verifyMomoCallbackSignature(Map<String, String> fields, String secretKey, String signature) {
        List<String> rawCandidates = new ArrayList<>();
        rawCandidates.add(buildMomoCallbackRaw(fields));
        rawCandidates.add(buildMomoCallbackRawWithoutAccessKey(fields));
        rawCandidates.add(buildMomoCallbackRawSorted(fields, true));
        rawCandidates.add(buildMomoCallbackRawSorted(fields, false));

        for (String raw : rawCandidates) {
            String expected = CryptoUtils.hmacSha256Hex(secretKey, raw);
            if (expected.equalsIgnoreCase(signature)) {
                return true;
            }
        }
        return false;
    }

    private static String buildMomoCallbackRawWithoutAccessKey(Map<String, String> f) {
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

        return "amount=" + amount
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

    private static String buildMomoCallbackRawSorted(Map<String, String> fields, boolean includeBlankValues) {
        return fields.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .filter(e -> !"signature".equalsIgnoreCase(e.getKey()))
                .filter(e -> includeBlankValues || trimToNull(e.getValue()) != null)
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + "=" + Objects.toString(e.getValue(), ""))
                .collect(Collectors.joining("&"));
    }

    private static String enrichMomoConfigError(String message, String redirectUrl, String ipnUrl) {
        StringBuilder sb = new StringBuilder(message == null ? "" : message);
        sb.append(" | Kiểm tra cấu hình MoMo: partner-code/access-key/secret-key phải là bộ test còn hiệu lực; ");
        sb.append("app.payment.backend-base-url phải là public HTTPS (không dùng localhost), ");
        sb.append("hiện redirectUrl=").append(redirectUrl).append(", ipnUrl=").append(ipnUrl);
        return sb.toString();
    }
}
