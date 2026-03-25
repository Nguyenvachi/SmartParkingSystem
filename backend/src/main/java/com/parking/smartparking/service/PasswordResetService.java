package com.parking.smartparking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.response.ForgotPasswordResponse;
import com.parking.smartparking.entity.PasswordResetToken;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.PasswordResetTokenRepository;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.password-reset.expiry-minutes:15}")
    private int expiryMinutes;

    @Value("${app.security.password-reset.expose-token:false}")
    private boolean exposeToken;

    @Transactional
    public ForgotPasswordResponse createResetToken(String email) {
        // Tránh lộ thông tin tồn tại user: luôn trả OK.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ForgotPasswordResponse.builder()
                    .message("Nếu email tồn tại, hệ thống đã tạo yêu cầu reset mật khẩu. Vui lòng kiểm tra email hoặc dùng token demo.")
                    .build();
        }

        // Có thể cho phép reset kể cả user bị disable (để admin mở lại). Nếu muốn chặn thì đổi logic ở đây.
        String rawToken = generateToken();
        String tokenHash = passwordEncoder.encode(rawToken);

        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();

        passwordResetTokenRepository.save(entity);

        return ForgotPasswordResponse.builder()
                .message("Nếu email tồn tại, hệ thống đã tạo yêu cầu reset mật khẩu. Vui lòng kiểm tra email hoặc dùng token demo.")
                .resetToken(exposeToken ? rawToken : null)
                .build();
    }

    @Transactional
    public void resetPassword(String email, String rawToken, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email hoặc token không hợp lệ."));

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken tokenEntity = passwordResetTokenRepository
                .findTopByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), now)
                .orElseThrow(() -> new RuntimeException("Email hoặc token không hợp lệ."));

        if (tokenEntity.isUsed() || tokenEntity.isExpired(now)) {
            throw new RuntimeException("Token đã hết hạn hoặc đã được sử dụng.");
        }

        if (!passwordEncoder.matches(rawToken, tokenEntity.getTokenHash())) {
            throw new RuntimeException("Email hoặc token không hợp lệ.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenEntity.setUsedAt(now);
        passwordResetTokenRepository.save(tokenEntity);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
