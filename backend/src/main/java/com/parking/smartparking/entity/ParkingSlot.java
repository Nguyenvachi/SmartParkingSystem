package com.parking.smartparking.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity ParkingSlot - Ánh xạ bảng 'parking_slots' trong Database Phục vụ cho
 * Tính năng #1: Concurrency Handling (Xử lý xung đột đặt chỗ) Tech Key:
 * Optimistic Locking với @Version
 */
@Entity
@Table(name = "parking_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_name", unique = true, nullable = false, length = 10)
    private String slotName; // Ví dụ: A01, B02, C10

    @Column(nullable = false, length = 20)
    private String type; // SEDAN (Xe con), SUV (Xe lớn)

    @Builder.Default
    @Column(length = 20)
    private String status = "AVAILABLE"; // AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE

    @Builder.Default
    @Column(name = "price_per_hour", precision = 10, scale = 2)
    private BigDecimal pricePerHour = BigDecimal.valueOf(5000.00);

    /**
     * ⚠️ CỰC KỲ QUAN TRỌNG: Cột version cho Optimistic Locking
     *
     * Kịch bản: User A và User B cùng chọn slot này lúc 10:00:00 - Cả 2 đều đọc
     * được version = 1 - User A bấm đặt trước 1ms -> Spring JPA tự động tăng
     * version lên 2 - User B bấm đặt sau -> Spring JPA kiểm tra version DB (2)
     * != version B đọc (1) - Hệ thống throw OptimisticLockingFailureException
     * -> User B nhận thông báo lỗi
     *
     * Tech Key: Tính năng #1 - Concurrency Handling
     */
    @Version
    private Long version;
}
