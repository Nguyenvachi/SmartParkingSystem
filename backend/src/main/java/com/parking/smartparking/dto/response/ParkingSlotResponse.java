package com.parking.smartparking.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về thông tin Parking Slot Sử dụng cho API: GET /api/slots
 *
 * Response JSON mẫu: { "id": 1, "slotName": "A01", "type": "SEDAN", "status":
 * "AVAILABLE", "pricePerHour": 5000.00, "version": 0 }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSlotResponse {

    private Long id;
    private String slotName;
    private String type;
    private String status;
    private BigDecimal pricePerHour;
    private String branchCode;

    /**
     * Version field cho frontend (để hiển thị trạng thái cập nhật) KHÔNG dùng
     * để client gửi lên (JPA tự quản lý)
     */
    private Long version;
}
