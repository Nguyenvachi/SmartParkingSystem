package com.parking.smartparking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WalletTopUpRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "1000.0", message = "Số tiền nạp phải từ 1.000 VND")
    private BigDecimal amount;

    @Size(max = 255, message = "Ghi chú không được quá 255 ký tự")
    private String description;
}
