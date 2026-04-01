package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO cho API Đăng nhập
 *
 * Frontend sẽ gửi JSON: { "email": "user@example.com", "password": "123456" }
 */
@Data
public class AuthRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
