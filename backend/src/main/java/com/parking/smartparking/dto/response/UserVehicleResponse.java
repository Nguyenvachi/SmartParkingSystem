package com.parking.smartparking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVehicleResponse {

    private Long id;
    private String plateNumber;
    private String vehicleType;
    private String color;
}
