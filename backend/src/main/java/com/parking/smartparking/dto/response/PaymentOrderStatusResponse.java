package com.parking.smartparking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PaymentOrderStatusResponse(
        String provider,
        String orderId,
        String status,
        BigDecimal amount,
        String message,
        LocalDateTime createdAt,
        LocalDateTime paidAt
        ) {

}
