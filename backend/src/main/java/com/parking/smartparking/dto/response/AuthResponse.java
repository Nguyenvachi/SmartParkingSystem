package com.parking.smartparking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về sau khi Đăng nhập/Đăng ký thành công
 *
 * Response JSON mẫu: { "userId": 1, "fullName": "Nguyen Van A", "email":
 * "user@example.com", "role": "ROLE_USER", "message": "Đăng nhập thành công!" }
 *
 * ⚠️ LƯU Ý: KHÔNG trả về password trong response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String role;

    // [FIX 2 - JWT] Token để Frontend lưu vào localStorage và gửi trong Authorization header
    private String token;

    private String message;
}
