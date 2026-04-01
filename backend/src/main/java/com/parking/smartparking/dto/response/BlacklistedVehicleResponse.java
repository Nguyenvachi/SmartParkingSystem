package com.parking.smartparking.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedVehicleResponse {

    private Long id;
    private String plateNumber;
    private String branchCode;
    private String reason;
    private Boolean active;
    private String createdBy;
    private LocalDateTime createdAt;
}
