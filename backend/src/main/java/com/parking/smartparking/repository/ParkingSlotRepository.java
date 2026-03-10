package com.parking.smartparking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.ParkingSlot;

/**
 * Repository cho Entity ParkingSlot Phục vụ cho việc quản lý bãi đỗ xe
 */
@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    /**
     * Tìm tất cả slot theo trạng thái
     *
     * @param status - AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
     * @return Danh sách slot
     */
    List<ParkingSlot> findByStatus(String status);

    List<ParkingSlot> findByStatusOrderBySlotNameAsc(String status);

    /**
     * Tìm slot theo tên (Ví dụ: A01)
     *
     * @param slotName - Tên slot
     * @return Optional<ParkingSlot>
     */
    Optional<ParkingSlot> findBySlotName(String slotName);
}
