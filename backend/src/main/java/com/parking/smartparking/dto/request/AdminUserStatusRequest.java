package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusRequest {

    @NotNull(message = "Trạng thái active không được để trống")
    private Boolean active;
}
