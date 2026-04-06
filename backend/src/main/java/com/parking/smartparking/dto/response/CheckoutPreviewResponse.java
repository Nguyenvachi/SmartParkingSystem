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
public class CheckoutPreviewResponse {

    private Long bookingId;
    private Long userId;
    private String userFullName;
    private String slotName;
    private String vehiclePlate;

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String appliedVoucherCode;

    private String note;
}
