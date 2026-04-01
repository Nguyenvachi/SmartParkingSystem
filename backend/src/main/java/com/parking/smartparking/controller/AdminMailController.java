package com.parking.smartparking.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.AdminMailTestRequest;
import com.parking.smartparking.dto.response.MessageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
public class AdminMailController {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@smartparking.local}")
    private String mailFrom;

    @PostMapping("/test")
    public ResponseEntity<MessageResponse> sendTestMail(
            @Valid @RequestBody AdminMailTestRequest request,
            Authentication authentication) {

        // Admin-only endpoint; safe to return explicit diagnostics.
        if (!mailEnabled) {
            return ResponseEntity.badRequest().body(MessageResponse.builder()
                    .message("Mail đang tắt (app.mail.enabled=false). Bật APP_MAIL_ENABLED=true và cấu hình spring.mail.*")
                    .build());
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return ResponseEntity.badRequest().body(MessageResponse.builder()
                    .message("Mail sender chưa sẵn sàng. Kiểm tra SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD")
                    .build());
        }

        String actor = authentication != null ? authentication.getName() : "unknown";

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(request.getTo().trim());
            message.setSubject(request.getSubject().trim());
            message.setText(request.getBody().trim() + "\n\n(sent by: " + actor + ")");
            sender.send(message);

            return ResponseEntity.ok(MessageResponse.builder()
                    .message("Đã gửi test mail tới: " + request.getTo().trim())
                    .build());
        } catch (MailException e) {
            return ResponseEntity.internalServerError().body(MessageResponse.builder()
                    .message("Gửi mail thất bại (SMTP): " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(MessageResponse.builder()
                    .message("Gửi mail thất bại: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
                    .build());
        }
    }
}
