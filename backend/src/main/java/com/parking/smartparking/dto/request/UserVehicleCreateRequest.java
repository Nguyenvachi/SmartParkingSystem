package com.parking.smartparking.dto.request;

import com.parking.smartparking.entity.UserVehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserVehicleCreateRequest {

    @NotBlank(message = "Biển số không được để trống")
    @Size(max = 20, message = "Biển số tối đa 20 ký tự")
    private String plateNumber;

    @NotNull(message = "Loại xe không được để trống")
    private UserVehicle.VehicleType vehicleType;

    @Size(max = 30, message = "Màu sắc tối đa 30 ký tự")
    private String color;
}
