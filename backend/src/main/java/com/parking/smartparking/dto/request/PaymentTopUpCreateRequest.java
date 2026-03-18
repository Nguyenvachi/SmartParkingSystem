package com.parking.smartparking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PaymentTopUpCreateRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    private String description;
}
