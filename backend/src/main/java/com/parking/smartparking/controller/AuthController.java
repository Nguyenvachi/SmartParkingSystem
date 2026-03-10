package com.parking.smartparking.controller;

import com.parking.smartparking.dto.request.AuthRequest;
import com.parking.smartparking.dto.request.RegisterRequest;
import com.parking.smartparking.dto.response.AuthResponse;
import com.parking.smartparking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller cho Authentication
 *
 * Base URL: /api/auth
 *
 * Endpoints: - POST /api/auth/register : Đăng ký tài khoản mới - POST
 * /api/auth/login : Đăng nhập
 *
 * Quy tắc: - @RestController: Tự động chuyển return value thành JSON -
 * @RequestMapping: Định nghĩa base path - @Valid: Kích hoạt validation (kiểm
 * tra @NotBlank, @Email...) - @RequestBody: Parse JSON từ request thành Object
 * Java
 */

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * API Đăng ký
     * 
     * Request:
     * POST /api/auth/register
     * Content-Type: application/json
     * Body:
     * {
     *   "fullName": "Nguyen Van A",
     *   "email": "test@example.com",
     *   "password": "123456"
     * }
     * 
     * Response (200 OK):
     * {
     *   "userId": 1,
     *   "fullName": "Nguyen Van A",
     *   "email": "test@example.com",
     *   "role": "ROLE_USER",
     *   "message": "Đăng ký tài khoản thành công!"
     * }
     * 
     * @param request - Dữ liệu đăng ký (đã validate)
     * @return ResponseEntity<AuthResponse>
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API Đăng nhập
     * 
     * Request:
     * POST /api/auth/login
     * Content-Type: application/json
     * Body:
     * {
     *   "email": "test@example.com",
     *   "password": "123456"
     * }
     * 
     * Response (200 OK):
     * {
     *   "userId": 1,
     *   "fullName": "Nguyen Van A",
     *   "email": "test@example.com",
     *   "role": "ROLE_USER",
     *   "message": "Đăng nhập thành công!"
     * }
     * 
     * @param request - Email và mật khẩu (đã validate)
     * @return ResponseEntity<AuthResponse>
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
