package com.parking.smartparking.entity;

/**
 * Enum AuthProvider - Phân biệt nguồn đăng ký
 *
 * Tech Key #10: Middleware/Filter - Quản lý nhiều phương thức xác thực
 *
 * LOCAL: Đăng ký bằng email/password truyền thống GOOGLE: Đăng nhập bằng Google
 * OAuth2
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
