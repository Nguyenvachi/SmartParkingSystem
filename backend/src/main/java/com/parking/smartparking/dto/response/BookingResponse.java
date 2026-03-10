package com.parking.smartparking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO trả về thông tin Booking
 *
 * Response JSON mẫu: { "bookingId": 1, "userFullName": "Nguyen Van A",
 * "slotName": "A01", "status": "PENDING", "bookingTime": "2026-03-10T11:30:00",
 * "expiryTime": "2026-03-10T11:45:00", "qrCodeBase64": "iVBORw0KGgo...",
 * "message": "Đặt chỗ thành công!" }
 *
 * Quan hệ: File Con dùng trong BookingService.convertToResponse() và
 * BookingController làm return type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long bookingId;

    // Thông tin User
    private Long userId;
    private String userFullName;

    // Thông tin Slot
    private Long slotId;
    private String slotName;
    private String slotType;

    // Trạng thái & thời gian
    private String status;
    private LocalDateTime bookingTime;
    private LocalDateTime expiryTime;    // expiryTime = bookingTime + 15 phút
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;

    // Tài chính (Phase 4 sẽ bổ sung)
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String appliedVoucherCode;

    /**
     * QR Code dạng Base64 PNG (Tech Key #7) Frontend render:
     * <img src="data:image/png;base64,{qrCodeBase64}" />
     */
    private String qrCodeBase64;

    // Thông báo cho Frontend
    private String message;
}
