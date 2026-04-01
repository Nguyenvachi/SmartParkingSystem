package com.parking.smartparking.service;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.response.ParkingSlotResponse;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BookingSchedulerService - Job tự động hủy vé quá hạn
 *
 * Tech Key #2: Spring Scheduler (@Scheduled)
 *
 * Cơ chế hoạt động: 1. Chạy tự động mỗi 60 giây (fixedDelay = 60000ms) 2. Tìm
 * tất cả booking PENDING có expiryTime < NOW() 3. Đổi status → CANCELLED 4. Trả
 * slot về AVAILABLE 5. Broadcast WebSocket để map real-time cập nhật (Tech Key
 * #5)
 *
 * Lưu ý: @EnableScheduling phải được khai báo ở
 * SmartParkingBackendApplication.java
 *
 * Quan hệ: - File Con (Dependency): BookingRepository, ParkingSlotRepository,
 * WebSocketController - File Cha (Kích hoạt) :
 * SmartParkingBackendApplication.java (@EnableScheduling)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSchedulerService {

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final WebSocketController webSocketController;

    /**
     * Job quét và hủy booking quá hạn 15 phút
     *
     * @Scheduled(fixedDelay = 60000): - fixedDelay: Đợi 60s SAU KHI job trước
     * hoàn thành mới chạy lại (khác với fixedRate là chạy cứ đúng 60s bất kể
     * job trước xong chưa) - Ưu điểm: Tránh overlapping nếu job chạy lâu hơn
     * 60s
     *
     * SQL equivalent của query: SELECT * FROM bookings WHERE status = 'PENDING'
     * AND expiry_time < NOW()
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelExpiredBookings() {
        LocalDateTime now = LocalDateTime.now();

        List<Booking> expiredBookings = bookingRepository
                .findByStatusAndExpiryTimeBefore(Booking.BookingStatus.PENDING, now);

        if (expiredBookings.isEmpty()) {
            log.debug("⏰ Scheduler: Không có booking hết hạn lúc {}", now);
            return;
        }

        int cancelledCount = 0;

        for (Booking booking : expiredBookings) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);

            ParkingSlot slot = booking.getParkingSlot();

            // Chỉ trả slot về AVAILABLE nếu slot đang ở RESERVED
            // (tránh conflict với slot đang OCCUPIED hoặc MAINTENANCE)
            if ("RESERVED".equals(slot.getStatus())) {
                slot.setStatus("AVAILABLE");
                parkingSlotRepository.save(slot);

                // WebSocket: Slot trống lại → Dashboard tự cập nhật màu xanh (Tech Key #5)
                webSocketController.sendSlotUpdate(ParkingSlotResponse.builder()
                        .id(slot.getId())
                        .slotName(slot.getSlotName())
                        .type(slot.getType())
                        .status("AVAILABLE")
                        .pricePerHour(slot.getPricePerHour())
                        .version(slot.getVersion())
                        .build());

                cancelledCount++;
            }

            log.info("⏰ Auto-cancelled booking #{} (user: {}, slot: {}, expired: {})",
                    booking.getId(),
                    booking.getUser().getFullName(),
                    slot.getSlotName(),
                    booking.getExpiryTime());
        }

        bookingRepository.saveAll(expiredBookings);

        log.info("⏰ Scheduler hoàn tất: Hủy {} booking hết hạn, trả {} slot về AVAILABLE",
                expiredBookings.size(), cancelledCount);
    }
}
