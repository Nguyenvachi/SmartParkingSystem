package com.parking.smartparking.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardSummaryResponse {

    private String role;
    private String branchCode;
    private boolean globalAdmin;
    private long totalVisibleUsers;
    private long totalVisibleSlots;
    private long availableSlots;
    private long reservedSlots;
    private long occupiedSlots;
    private long maintenanceSlots;
    private long activeBlacklistEntries;

    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private BigDecimal revenueAllTime;

    private long completedBookingsToday;
    private long completedBookingsThisMonth;
}
