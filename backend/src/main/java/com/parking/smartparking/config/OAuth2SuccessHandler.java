package com.parking.smartparking.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.parking.smartparking.entity.AuthProvider;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.service.JwtService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2SuccessHandler - Xử lý sau khi đăng nhập Google thành công
 *
 * Chức năng: 1. Lấy thông tin user từ OAuth2User 2. Redirect về frontend với
 * query params 3. Frontend sẽ lưu vào localStorage
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService; // [FIX 5] Tạo JWT cho OAuth2 user

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.frontend.login-path:/login}")
    private String frontendLoginPath;

    @Value("${app.frontend.dashboard-path:/dashboard}")
    private String frontendDashboardPath;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        log.info("✅ Google Login thành công: {}", email);

        // Ensure user exists in DB (first-time login creates user)
        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    // Keep existing role, just refresh profile fields
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
                .fullName(name != null ? name : email)
                .password("")
                .role(User.Role.ROLE_USER)
                .authProvider(AuthProvider.GOOGLE)
                .avatarUrl(picture)
                .isEmailVerified(true)
                .build());

        try {
            userRepository.save(Objects.requireNonNull(user));
        } catch (Exception ex) {
            log.error("❌ Không thể lưu user OAuth2 vào DB (check schema users table)", ex);
            response.sendRedirect(frontendBaseUrl + frontendLoginPath + "?error=oauth2_db");
            return;
        }

        // [FIX 5] Tạo JWT token cho OAuth2 user (giống login thường)
        String jwtToken = jwtService.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId());

        String redirectUrl = String.format(
                "%s%s?userId=%d&email=%s&fullName=%s&role=%s&branchCode=%s&avatarUrl=%s&token=%s",
                frontendBaseUrl,
                frontendDashboardPath,
                user.getId(),
                URLEncoder.encode(email, StandardCharsets.UTF_8),
                URLEncoder.encode(user.getFullName() != null ? user.getFullName() : "", StandardCharsets.UTF_8),
                user.getRole().name(),
                URLEncoder.encode(user.getBranchCode() != null ? user.getBranchCode() : "", StandardCharsets.UTF_8),
                URLEncoder.encode(user.getAvatarUrl() != null ? user.getAvatarUrl() : "", StandardCharsets.UTF_8),
                URLEncoder.encode(jwtToken, StandardCharsets.UTF_8));

        log.info("🚀 Redirect về: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }
}
