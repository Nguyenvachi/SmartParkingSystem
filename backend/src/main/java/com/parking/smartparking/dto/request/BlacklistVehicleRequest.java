package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BlacklistVehicleRequest {

    @NotBlank(message = "Biển số không được để trống")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{1,2}[0-9]{4,6}$", message = "Biển số xe không đúng định dạng hỗ trợ")
    private String plateNumber;

    @Pattern(regexp = "^$|^[A-Z0-9_-]{2,20}$", message = "Mã chi nhánh chỉ gồm chữ in hoa, số, _ hoặc -")
    private String branchCode;

    private String reason;
}
