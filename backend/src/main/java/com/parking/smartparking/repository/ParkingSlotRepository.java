package com.parking.smartparking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select slot from ParkingSlot slot where coalesce(slot.branchCode, 'MAIN') = :branchCode order by slot.slotName asc")
    List<ParkingSlot> findAllVisibleByBranchCode(@Param("branchCode") String branchCode);

    @Query("select slot from ParkingSlot slot where slot.status = :status and coalesce(slot.branchCode, 'MAIN') = :branchCode order by slot.slotName asc")
    List<ParkingSlot> findByStatusVisibleByBranchCode(@Param("status") String status, @Param("branchCode") String branchCode);

    @Query("select slot from ParkingSlot slot where slot.id = :id and coalesce(slot.branchCode, 'MAIN') = :branchCode")
    Optional<ParkingSlot> findByIdVisibleByBranchCode(@Param("id") Long id, @Param("branchCode") String branchCode);

    /**
     * Tìm slot theo tên (Ví dụ: A01)
     *
     * @param slotName - Tên slot
     * @return Optional<ParkingSlot>
     */
    Optional<ParkingSlot> findBySlotName(String slotName);
}
