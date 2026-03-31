package com.parking.smartparking.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.smartparking.dto.response.AuthResponse;
import com.parking.smartparking.entity.AuthProvider;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GoogleIdTokenAuthService {

    private static final String TOKENINFO_ENDPOINT = "https://oauth2.googleapis.com/tokeninfo";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${app.security.google.allowed-audiences:}")
    private String allowedAudiences;

    public AuthResponse loginWithIdToken(String idToken) {
        Map<String, Object> tokenInfo = verifyWithTokenInfo(idToken);

        String email = asString(tokenInfo.get("email"));
        String name = asString(tokenInfo.get("name"));
        String picture = asString(tokenInfo.get("picture"));

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google token không hợp lệ (thiếu email).");
        }

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    if (name != null && !name.isBlank()) {
                        existing.setFullName(name);
                    }
                    existing.setAuthProvider(AuthProvider.GOOGLE);
                    existing.setIsEmailVerified(true);
                    if (picture != null && !picture.isBlank()) {
                        existing.setAvatarUrl(picture);
                    }
                    return existing;
                })
                .orElseGet(() -> User.builder()
                .email(email)
                .fullName(name != null && !name.isBlank() ? name : email)
                .password("")
                .role(User.Role.ROLE_USER)
                .authProvider(AuthProvider.GOOGLE)
                .avatarUrl(picture)
                .isEmailVerified(true)
                .build());

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.");
        }

        user = userRepository.save(Objects.requireNonNull(user));

        if (user.getRole() != User.Role.ROLE_USER) {
            throw new RuntimeException("App mobile chỉ dành cho khách (ROLE_USER).");
        }

        String jwtToken = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .branchCode(user.getBranchCode())
                .token(jwtToken)
                .message("Đăng nhập Google thành công!")
                .build();
    }

    private Map<String, Object> verifyWithTokenInfo(String idToken) {
        try {
            String url = TOKENINFO_ENDPOINT + "?id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new RuntimeException("Google token không hợp lệ.");
            }

            Map<String, Object> map = objectMapper.readValue(res.body(), new TypeReference<Map<String, Object>>() {
            });

            // Validate aud if configured (avoid accidentally accepting other apps' tokens).
            String aud = asString(map.get("aud"));
            Set<String> validAudiences = buildValidAudiences();
            if (!validAudiences.isEmpty()) {
                if (aud == null || !validAudiences.contains(aud)) {
                    throw new RuntimeException("Google token không thuộc ứng dụng này (aud mismatch).");
                }
            }

            return map;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Không thể xác thực Google token.");
        }
    }

    private Set<String> buildValidAudiences() {
        Set<String> fromConfig = Set.of(
                safe(googleClientId),
                safe(allowedAudiences))
                .stream()
                .flatMap(s -> s.isBlank() ? java.util.stream.Stream.empty()
                : java.util.stream.Stream.of(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> !s.contains("your-google-client-id"))
                .collect(Collectors.toSet());

        return fromConfig;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
