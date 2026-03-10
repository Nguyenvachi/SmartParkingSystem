package com.parking.smartparking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO cho việc tạo mới hoặc cập nhật Parking Slot Sử dụng cho API: POST
 * /api/slots, PUT /api/slots/{id}
 *
 * Tech Key: Tính năng #1 - Concurrency (version được JPA tự động quản lý)
 */
@Data
public class ParkingSlotRequest {

    @NotBlank(message = "Tên slot không được để trống")
    @Size(max = 10, message = "Tên slot không được quá 10 ký tự")
    @Pattern(regexp = "^[A-Z]\\d{2}$", message = "Tên slot phải có định dạng A01, B02, C10...")
    private String slotName; // Ví dụ: A01, B15, C20

    @NotBlank(message = "Loại xe không được để trống")
    @Pattern(regexp = "^(SEDAN|SUV)$", message = "Loại xe chỉ được là SEDAN hoặc SUV")
    private String type; // SEDAN hoặc SUV

    @Pattern(regexp = "^(AVAILABLE|RESERVED|OCCUPIED|MAINTENANCE)$",
            message = "Trạng thái không hợp lệ")
    private String status = "AVAILABLE"; // Mặc định là AVAILABLE

    @DecimalMin(value = "0.0", message = "Giá phải lớn hơn hoặc bằng 0")
    private BigDecimal pricePerHour = BigDecimal.valueOf(5000.00);
}
