package com.parking.smartparking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity User - Ánh xạ bảng 'users' trong Database Phục vụ cho Tính năng:
 * Authentication & RBAC (Role-Based Access Control) Tech Key: Spring Security +
 * JPA
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "is_email_verified", nullable = false)
    private Boolean isEmailVerified = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Enum định nghĩa vai trò trong hệ thống ROLE_USER: Người dùng thường (Đặt
     * vé, Xem lịch sử) ROLE_ADMIN: Quản trị viên (Quản lý toàn bộ hệ thống)
     */
    public enum Role {
        ROLE_USER,
        ROLE_ADMIN
    }
}
