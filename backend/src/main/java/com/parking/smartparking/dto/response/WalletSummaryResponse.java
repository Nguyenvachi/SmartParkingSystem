package com.parking.smartparking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {

    private Long userId;
    private String fullName;
    private BigDecimal walletBalance;
    private String membershipPlan;
    private LocalDateTime membershipExpiry;
    private Boolean autoRenewMembership;
    private BigDecimal monthlyMembershipFee;
    private List<WalletTransactionResponse> recentTransactions;
}
