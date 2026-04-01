package com.parking.smartparking.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.Booking;

/**
 * Repository cho Entity Booking
 *
 * Tech Key #2: findByStatusAndExpiryTimeBefore dùng cho Scheduler quét booking
 * PENDING đã hết 15 phút
 *
 * Quan hệ: - File Con (Dependency): Booking.java (Entity) - File Cha (Sử dụng)
 * : BookingService.java, BookingSchedulerService.java
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Tìm các booking đã hết hạn (Dùng cho Scheduler - Tech Key #2)
     *
     * SQL tương đương: SELECT * FROM bookings WHERE status = 'PENDING' AND
     * expiry_time < NOW()
     *
     * @param status - Chỉ quét PENDING (đã đặt nhưng chưa vào bãi)
     * @param time - Mốc thời gian hiện tại để so sánh
     * @return Danh sách booking hết hạn cần hủy
     */
    List<Booking> findByStatusAndExpiryTimeBefore(Booking.BookingStatus status, LocalDateTime time);

    /**
     * Lấy lịch sử booking của 1 user, mới nhất trước Navigation: user → email
     * (User entity có field email)
     *
     * @param email - Email của user
     * @return Danh sách booking theo thứ tự mới nhất trước
     */
    List<Booking> findByUser_EmailOrderByBookingTimeDesc(String email);

    /**
     * Lấy booking theo ID và email user (Security: chỉ xem booking của chính
     * mình)
     *
     * @param id - ID booking
     * @param email - Email user phải trùng với owner
     * @return Optional<Booking>
     */
    Optional<Booking> findByIdAndUser_Email(Long id, String email);

    /**
     * Kiểm tra user có đang có booking active cho slot không Ngăn 1 user đặt
     * cùng 1 slot nhiều lần
     *
     * @param email - Email user
     * @param slotId - ID slot
     * @param statuses - Danh sách trạng thái cần check (PENDING, CHECKED_IN)
     * @return true nếu đã có booking active
     */
    boolean existsByUser_EmailAndParkingSlot_IdAndStatusIn(
            String email, Long slotId, List<Booking.BookingStatus> statuses);

    /**
     * Kiểm tra user có đang có booking active (PENDING hoặc CHECKED_IN) không.
     *
     * Rule mới (Phase 1): 1 user chỉ được có tối đa 1 booking đang active.
     */
    boolean existsByUser_EmailAndStatusIn(String email, List<Booking.BookingStatus> statuses);

    List<Booking> findByParkingSlot_IdInAndStatus(List<Long> slotIds, Booking.BookingStatus status);

    Optional<Booking> findFirstByParkingSlot_IdAndStatusOrderByCheckInTimeDesc(Long slotId, Booking.BookingStatus status);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Booking b
            where b.status = :status
              and b.checkOutTime >= :start
              and b.checkOutTime < :end
            """)
    BigDecimal sumTotalAmountByStatusAndCheckOutTimeBetween(
            @Param("status") Booking.BookingStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Booking b
            where b.status = :status
              and b.checkOutTime >= :start
              and b.checkOutTime < :end
              and upper(coalesce(b.parkingSlot.branchCode, 'MAIN')) = :branchCode
            """)
    BigDecimal sumTotalAmountByStatusAndCheckOutTimeBetweenForBranch(
            @Param("status") Booking.BookingStatus status,
            @Param("branchCode") String branchCode,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select count(b)
            from Booking b
            where b.status = :status
              and b.checkOutTime >= :start
              and b.checkOutTime < :end
            """)
    long countByStatusAndCheckOutTimeBetween(
            @Param("status") Booking.BookingStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select count(b)
            from Booking b
            where b.status = :status
              and b.checkOutTime >= :start
              and b.checkOutTime < :end
              and upper(coalesce(b.parkingSlot.branchCode, 'MAIN')) = :branchCode
            """)
    long countByStatusAndCheckOutTimeBetweenForBranch(
            @Param("status") Booking.BookingStatus status,
            @Param("branchCode") String branchCode,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Booking b
            where b.status = :status
            """)
    BigDecimal sumTotalAmountByStatus(@Param("status") Booking.BookingStatus status);

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Booking b
            where b.status = :status
              and upper(coalesce(b.parkingSlot.branchCode, 'MAIN')) = :branchCode
            """)
    BigDecimal sumTotalAmountByStatusForBranch(
            @Param("status") Booking.BookingStatus status,
            @Param("branchCode") String branchCode);
}
