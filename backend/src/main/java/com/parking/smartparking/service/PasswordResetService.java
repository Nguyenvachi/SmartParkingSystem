package com.parking.smartparking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.response.ForgotPasswordResponse;
import com.parking.smartparking.entity.PasswordResetToken;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.PasswordResetTokenRepository;
import com.parking.smartparking.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.password-reset.expiry-minutes:15}")
    private int expiryMinutes;

    @Value("${app.security.password-reset.expose-token:false}")
    private boolean exposeToken;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@smartparking.local}")
    private String mailFrom;

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

        // Best-effort email sending (do not leak user existence or fail request)
        trySendResetEmail(user, rawToken);

        return ForgotPasswordResponse.builder()
                .message("Nếu email tồn tại, hệ thống đã tạo yêu cầu reset mật khẩu. Vui lòng kiểm tra email hoặc dùng token demo.")
                .resetToken(exposeToken ? rawToken : null)
                .build();
    }

    private void trySendResetEmail(User user, String rawToken) {
        if (!mailEnabled) {
            return;
        }
        if (user == null) {
            return;
        }
        String to = user.getEmail() != null ? user.getEmail().trim() : "";
        if (to.isBlank()) {
            return;
        }
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Mail enabled but JavaMailSender is not available; check spring.mail.* configuration");
            return;
        }

        // Prefer HTML email; fallback to plain text if needed.
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("[SmartParking] Reset mật khẩu");

            String htmlContent = renderResetMailHtml(user, rawToken);
            helper.setText(renderResetMailText(user, rawToken), htmlContent);

            mailSender.send(mimeMessage);
            return;
        } catch (Exception e) {
            log.warn("Failed to send HTML reset-password email to {}", to, e);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("[SmartParking] Reset mật khẩu");
            message.setText(renderResetMailText(user, rawToken));
            mailSender.send(message);
        } catch (MailException e) {
            // Best-effort: do not fail forgot-password flow on mail issues.
            log.warn("Failed to send reset-password email to {}", to, e);
        }
    }

    private String renderResetMailHtml(User user, String rawToken) {
        String email = user != null && user.getEmail() != null ? user.getEmail() : "";

        String template = """
                                <!doctype html>
                                <html lang=\"vi\">
                                    <head>
                                        <meta charset=\"utf-8\" />
                                        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                                        <title>Reset mật khẩu</title>
                                    </head>
                                    <body style=\"margin:0;padding:0;background:#f6f9fc;\">
                                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f6f9fc;padding:24px 12px;\">
                                            <tr>
                                                <td align=\"center\">
                                                    <table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;width:100%;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;\">
                                                        <tr>
                                                            <td style=\"padding:20px 22px;background:#0b5fff;\">
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:18px;line-height:22px;font-weight:700;color:#ffffff;\">Hệ thống Smart Parking</div>
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:16px;color:#dbeafe;margin-top:4px;\">Yêu cầu đặt lại mật khẩu</div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td style=\"padding:22px;\">
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:20px;color:#111827;\">
                                                                    Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản:
                                                                    <span style=\"font-weight:700;\">{{EMAIL}}</span>
                                                                </div>

                                                                <div style=\"margin-top:18px;padding:16px;border:1px dashed #93c5fd;background:#eff6ff;border-radius:10px;\">
                                                                    <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:16px;color:#1d4ed8;font-weight:700;text-transform:uppercase;letter-spacing:0.06em;\">Mã Token</div>
                                                                    <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:22px;line-height:28px;color:#111827;font-weight:800;text-align:center;margin-top:10px;word-break:break-all;\">{{TOKEN}}</div>
                                                                </div>

                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:18px;color:#374151;margin-top:14px;\">
                                                                    Token này sẽ hết hạn sau <span style=\"font-weight:700;\">{{EXPIRY_MINUTES}} phút</span>.
                                                                </div>

                                                                <div style=\"margin-top:16px;padding:12px 14px;border-left:4px solid #f59e0b;background:#fffbeb;border-radius:8px;\">
                                                                    <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:18px;color:#92400e;\">
                                                                        Lưu ý bảo mật: Không chia sẻ token với bất kỳ ai. Nếu bạn không yêu cầu reset mật khẩu, vui lòng bỏ qua email này.
                                                                    </div>
                                                                </div>

                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:16px;color:#6b7280;margin-top:20px;\">
                                                                    Mở app SmartParking → Quên mật khẩu → Nhập token để đặt mật khẩu mới.
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td style=\"padding:14px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;\">
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:15px;color:#6b7280;\">
                                                                    Email này được gửi tự động từ hệ thống Smart Parking.
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </body>
                                </html>
                                """;

        return template
                .replace("{{EMAIL}}", escapeHtml(email))
                .replace("{{TOKEN}}", escapeHtml(rawToken))
                .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String renderResetMailText(User user, String rawToken) {
        StringBuilder sb = new StringBuilder();
        sb.append("Yêu cầu reset mật khẩu SmartParking\n\n");
        sb.append("Tài khoản: ").append(user.getEmail()).append("\n");
        sb.append("Token reset: ").append(rawToken).append("\n");
        sb.append("Hết hạn sau: ").append(expiryMinutes).append(" phút\n\n");
        sb.append("Mở app SmartParking > Quên mật khẩu > Nhập token trên để đặt mật khẩu mới.\n");
        sb.append("Nếu bạn không yêu cầu, hãy bỏ qua email này.\n");
        return sb.toString();
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
