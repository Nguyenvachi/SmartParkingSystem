package com.parking.smartparking.repository;

import com.parking.smartparking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
}
