package com.parking.smartparking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity Booking - Ánh xạ bảng 'bookings' trong Database
 *
 * Tech Key #1: Optimistic Locking - Chặn 2 user đặt cùng 1 slot → Cơ chế:
 *
 * @Version trên ParkingSlot (không phải Booking) → Khi 2 user cùng saveAndFlush
 * ParkingSlot, user đến sau bị ObjectOptimisticLockingFailureException
 *
 * Tech Key #2: @Scheduled sẽ quét booking PENDING hết expiryTime → CANCELLED
 * Tech Key #7: QR Code + Digital Signature lưu trong qrCodeBase64 + qrSignature
 *
 * Quan hệ: - File Con (Entity dùng): User.java, ParkingSlot.java - File Cha (Sử
 * dụng) : BookingService.java, BookingSchedulerService.java
 *
 * Trạng thái vòng đời: PENDING → CHECKED_IN → COMPLETED PENDING → CANCELLED
 * (quá 15 phút hoặc user tự hủy)
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Quan hệ N-1 với User: Nhiều booking thuộc về 1 user
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Quan hệ N-1 với ParkingSlot: Nhiều booking có thể đặt 1 slot (nhưng không
     * đồng thời)
     *
     * @Version trên ParkingSlot đảm bảo không ai đặt trùng trong cùng 1 thời
     * điểm
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_slot_id", nullable = false)
    private ParkingSlot parkingSlot;

    /**
     * Trạng thái booking PENDING : Đã đặt, chờ vào bãi (trong vòng 15 phút -
     * Tech Key #2) CHECKED_IN : Đã vào bãi, đang đỗ xe COMPLETED : Đã ra bãi và
     * thanh toán xong (Phase 4) CANCELLED : Đã hủy (tự hủy hoặc Scheduler tự
     * động hủy)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    /**
     * Thời điểm hết hạn = bookingTime + 15 phút Scheduler sẽ quét cột này để tự
     * động hủy (Tech Key #2)
     */
    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    /**
     * Tổng tiền - sẽ được tính khi Check-out (Phase 4 Dynamic Pricing)
     */
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * QR Code dạng Base64 PNG (Tech Key #7) Frontend dùng:
     * <img src="data:image/png;base64,{qrCodeBase64}" />
     */
    @Lob
    @Column(name = "qr_code", columnDefinition = "LONGTEXT")
    private String qrCodeBase64;

    /**
     * Chữ ký số HMAC-SHA256 của nội dung QR (Tech Key #7: Digital Signature)
     * Dùng để verify vé không bị giả mạo khi quét tại cổng
     */
    @Column(name = "qr_signature", length = 64)
    private String qrSignature;

    /**
     * Enum trạng thái vé
     */
    public enum BookingStatus {
        PENDING, // Đặt xong, chờ vào bãi (15 phút)
        CHECKED_IN, // Đang đỗ trong bãi
        COMPLETED, // Đã hoàn thành (checkout + thanh toán)
        CANCELLED    // Đã hủy
    }
}
