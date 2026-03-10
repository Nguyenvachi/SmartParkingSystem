package com.parking.smartparking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private String branchCode;
    private Boolean emailVerified;
    private BigDecimal walletBalance;
    private LocalDateTime membershipExpiry;
    private LocalDateTime createdAt;
}
