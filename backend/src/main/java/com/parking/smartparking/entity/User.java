package com.parking.smartparking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "is_email_verified", nullable = false)
    private Boolean isEmailVerified = false;

    @Builder.Default
    @Column(name = "wallet_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_plan", nullable = false, length = 20)
    private MembershipPlan membershipPlan = MembershipPlan.NONE;

    @Column(name = "membership_expiry")
    private LocalDateTime membershipExpiry;

    @Builder.Default
    @Column(name = "auto_renew_membership", nullable = false)
    private Boolean autoRenewMembership = false;

    @Column(name = "branch_code", length = 20)
    private String branchCode;

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
        ROLE_BRANCH_ADMIN,
        ROLE_ADMIN
    }

    public boolean isGlobalAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    public boolean isBranchAdmin() {
        return role == Role.ROLE_BRANCH_ADMIN;
    }

    public boolean isAdmin() {
        return isGlobalAdmin() || isBranchAdmin();
    }

    public enum MembershipPlan {
        NONE,
        MONTHLY
    }
}
