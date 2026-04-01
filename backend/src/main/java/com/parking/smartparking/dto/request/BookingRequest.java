package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO nhận request đặt chỗ từ Frontend
 *
 * Request JSON: { "slotId": 1 }
 *
 * Tech Key #1: BookingService sẽ dùng slotId để tìm ParkingSlot và trigger
 * Optimistic Locking khi cập nhật status slot
 *
 * Quan hệ: File Con của BookingController.java + BookingService.java
 */
@Data
public class BookingRequest {

    @NotNull(message = "Slot ID không được để trống")
    private Long slotId;

    @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{1,2}[0-9]{4,6}$", message = "Biển số xe không đúng định dạng hỗ trợ")
    private String vehiclePlate;
}
