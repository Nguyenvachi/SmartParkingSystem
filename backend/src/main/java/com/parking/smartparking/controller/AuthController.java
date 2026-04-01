package com.parking.smartparking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.AuthRequest;
import com.parking.smartparking.dto.request.ForgotPasswordRequest;
import com.parking.smartparking.dto.request.GoogleLoginRequest;
import com.parking.smartparking.dto.request.RegisterRequest;
import com.parking.smartparking.dto.request.ResetPasswordRequest;
import com.parking.smartparking.dto.response.AuthResponse;
import com.parking.smartparking.dto.response.ForgotPasswordResponse;
import com.parking.smartparking.dto.response.MessageResponse;
import com.parking.smartparking.service.AuthService;
import com.parking.smartparking.service.GoogleIdTokenAuthService;
import com.parking.smartparking.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API Controller cho Authentication
 *
 * Base URL: /api/auth
 *
 * Endpoints: - POST /api/auth/register : Đăng ký tài khoản mới - POST
 * /api/auth/login : Đăng nhập
 *
 * Quy tắc: - @RestController: Tự động chuyển return value thành JSON -
 *
 * @RequestMapping: Định nghĩa base path - @Valid: Kích hoạt validation (kiểm
 * tra @NotBlank, @Email...) - @RequestBody: Parse JSON từ request thành Object
 * Java
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final GoogleIdTokenAuthService googleIdTokenAuthService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(googleIdTokenAuthService.loginWithIdToken(request.getIdToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(passwordResetService.createResetToken(request.getEmail()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getEmail(),
                request.getToken(),
                request.getNewPassword(),
                request.getConfirmPassword());

        return ResponseEntity.ok(MessageResponse.builder()
                .message("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.")
                .build());
    }
}
