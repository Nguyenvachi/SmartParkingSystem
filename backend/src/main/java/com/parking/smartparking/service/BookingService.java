package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.request.BookingRequest;
import com.parking.smartparking.dto.request.CheckoutRequest;
import com.parking.smartparking.dto.response.BookingResponse;
import com.parking.smartparking.dto.response.ParkingSlotResponse;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BookingService - Xử lý toàn bộ logic đặt chỗ
 *
 * Tech Key #1: Optimistic Locking (@Version trên ParkingSlot) Kịch bản: A và B
 * cùng đặt slot A01 lúc 11:30:00.000 → A đọc slot version=1, B đọc slot
 * version=1 → A saveAndFlush: UPDATE parking_slots SET status='RESERVED',
 * version=2 WHERE id=? AND version=1 ✅ → B saveAndFlush: UPDATE parking_slots
 * SET status='RESERVED', version=2 WHERE id=? AND version=1 ❌ (0 rows updated)
 * → JPA throws ObjectOptimisticLockingFailureException → B nhận thông báo lỗi,
 * slot không bị double-book
 *
 * Tech Key #5: sendSlotUpdate() qua WebSocket sau mỗi thay đổi slot Tech Key
 * #7: Sinh QR Code + Chữ ký số ngay sau khi tạo booking
 *
 * Quan hệ: - File Con (Dependency): BookingRepository, ParkingSlotRepository,
 * UserRepository, QRCodeService, WebSocketController - File Cha (Sử dụng) :
 * BookingController.java
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final UserRepository userRepository;
    private final QRCodeService qrCodeService;
    private final WebSocketController webSocketController;
        private final PricingService pricingService;
        private final WalletService walletService;
        private final VoucherService voucherService;
        private final BlacklistService blacklistService;

    /**
     * ===== CORE LOGIC: TẠO BOOKING VỚI OPTIMISTIC LOCKING =====
     *
     * Luồng xử lý: 1. Xác định user từ JWT email 2. Tìm slot theo ID 3. Kiểm
     * tra slot AVAILABLE 4. Đổi slot → RESERVED (trigger Optimistic Lock check)
     * 5. Tạo Booking record với status PENDING 6. Sinh QR Code + Digital
     * Signature 7. Broadcast WebSocket real-time
     *
     * @param userEmail - Email lấy từ JWT Authentication
     * @param request - Chứa slotId cần đặt
     * @return BookingResponse với QR Code đính kèm
     */
    @Transactional
        @SuppressWarnings("null")
    public BookingResponse createBooking(String userEmail, BookingRequest request) {

        // Bước 1: Tìm user theo email từ JWT
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + userEmail));

        // Bước 2: Tìm slot
        Long requiredSlotId = Objects.requireNonNull(request.getSlotId(), "slotId không được null");
        ParkingSlot slot = parkingSlotRepository.findById(requiredSlotId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + request.getSlotId()));

        // Bước 3: Kiểm tra trạng thái slot
        if (!"AVAILABLE".equals(slot.getStatus())) {
            throw new RuntimeException(
                    "Slot " + slot.getSlotName() + " hiện không khả dụng (Trạng thái: " + slot.getStatus() + ")");
        }

        // Bước 4: Kiểm tra user đã có booking active cho slot này chưa
        boolean alreadyBooked = bookingRepository.existsByUser_EmailAndParkingSlot_IdAndStatusIn(
                userEmail, slot.getId(),
                List.of(Booking.BookingStatus.PENDING, Booking.BookingStatus.CHECKED_IN));
        if (alreadyBooked) {
            throw new RuntimeException("Bạn đã có booking active cho slot " + slot.getSlotName());
        }

        // Bước 5: Đổi slot → RESERVED
        // ⚠️ ĐÂY LÀ TRÁI TIM CỦA OPTIMISTIC LOCKING (Tech Key #1)
        // saveAndFlush() gửi UPDATE ngay (không đợi commit)
        // JPA tự thêm: WHERE id=? AND version=? vào câu SQL
        // Nếu version đã thay đổi (do user khác đặt trước) → 0 rows affected → Exception
        slot.setStatus("RESERVED");
        try {
            parkingSlotRepository.saveAndFlush(slot);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("⚡ Xung đột đặt chỗ: User [{}] cố đặt slot [{}] nhưng bị race condition",
                    userEmail, slot.getSlotName());
            throw new RuntimeException(
                    "Slot " + slot.getSlotName() + " vừa được người khác đặt mất! Vui lòng chọn slot khác.");
        }

        // Bước 6: Tạo Booking record
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .user(user)
                .parkingSlot(slot)
                .status(Booking.BookingStatus.PENDING)
                .bookingTime(now)
                .expiryTime(now.plusMinutes(15)) // Hết hạn sau 15 phút (Tech Key #2)
                .vehiclePlate(normalizeVehiclePlate(request.getVehiclePlate()))
                .build();

        Booking savedBooking = Objects.requireNonNull(bookingRepository.save(booking));

        // Bước 7: Sinh QR Code + Digital Signature (Tech Key #7)
        // Format: BOOKING:{id}|USER:{userId}|SLOT:{slotName}|TIME:{time}
        String qrContent = qrCodeService.buildBookingPayload(
                savedBooking.getId(),
                user.getId(),
                slot.getSlotName(),
                now,
                savedBooking.getVehiclePlate());
        String signature = qrCodeService.generateSignature(qrContent);
        String qrBase64 = qrCodeService.generateQRCode(qrContent + "|SIG:" + signature);

        savedBooking.setQrCodeBase64(qrBase64);
        savedBooking.setQrSignature(signature);
        bookingRepository.save(savedBooking);

        // Bước 8: WebSocket real-time broadcast (Tech Key #5)
        webSocketController.sendSlotUpdate(toSlotResponse(slot));

        log.info("✅ Booking #{} tạo thành công: User [{}] → Slot [{}], hết hạn lúc {}",
                savedBooking.getId(), userEmail, slot.getSlotName(), savedBooking.getExpiryTime());

        return toResponse(savedBooking, "Đặt chỗ thành công! Vui lòng vào bãi trong 15 phút.");
    }

    /**
     * Lấy lịch sử booking của user (mới nhất trước)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userEmail) {
        return bookingRepository.findByUser_EmailOrderByBookingTimeDesc(userEmail)
                .stream()
                .map(b -> toResponse(b, null))
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết 1 booking (chỉ owner mới xem được - Data Isolation cơ bản)
     */
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id, String userEmail) {
        Booking booking = bookingRepository.findByIdAndUser_Email(id, userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + id + " hoặc bạn không có quyền xem"));
        return toResponse(booking, null);
    }

    /**
     * Check-in: Vào bãi (PENDING → CHECKED_IN, slot → OCCUPIED)
     */
    @Transactional
    public BookingResponse checkIn(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findByIdAndUser_Email(bookingId, userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));

        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException(
                    "Booking không ở trạng thái PENDING (hiện tại: " + booking.getStatus() + ")");
        }

        if (LocalDateTime.now().isAfter(booking.getExpiryTime())) {
            throw new RuntimeException("Booking đã hết hạn 15 phút. Vui lòng tạo booking mới.");
        }

                validateBookingQrSignature(booking);
                blacklistService.assertVehicleAllowed(booking.getVehiclePlate(), booking.getParkingSlot().getBranchCode());

        booking.setStatus(Booking.BookingStatus.CHECKED_IN);
        booking.setCheckInTime(LocalDateTime.now());

        ParkingSlot slot = booking.getParkingSlot();
        slot.setStatus("OCCUPIED");
        parkingSlotRepository.save(slot);
        bookingRepository.save(booking);

        // WebSocket broadcast (Tech Key #5)
        webSocketController.sendSlotUpdate(toSlotResponse(slot));

        log.info("🚗 Check-in: Booking #{} → Slot [{}] OCCUPIED", bookingId, slot.getSlotName());
        return toResponse(booking, "Check-in thành công! Xe đã vào bãi.");
    }

    /**
     * Check-out: Ra bãi, tính tiền động và trừ ví (Phase 4)
     */
    @Transactional
    public BookingResponse checkOut(Long bookingId, String userEmail) {
        return checkOut(bookingId, userEmail, null);
    }

    @Transactional
    public BookingResponse checkOut(Long bookingId, String userEmail, CheckoutRequest request) {
        Booking booking = bookingRepository.findByIdAndUser_Email(bookingId, userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));

        if (booking.getStatus() != Booking.BookingStatus.CHECKED_IN) {
            throw new RuntimeException(
                    "Chỉ có thể check-out booking ở trạng thái CHECKED_IN (hiện tại: " + booking.getStatus() + ")");
        }

        if (booking.getCheckInTime() == null) {
            throw new RuntimeException("Booking chưa có thời gian check-in hợp lệ.");
        }

        User user = booking.getUser();
        LocalDateTime checkOutTime = LocalDateTime.now();
        PricingService.PricingResult pricingResult = pricingService.calculateCheckoutAmount(booking, user, checkOutTime);

        BigDecimal subtotalAmount = pricingResult.totalAmount();
        VoucherService.AppliedVoucher appliedVoucher = voucherService.applyVoucherToAmount(
                request != null ? request.getVoucherCode() : null,
                subtotalAmount);

        BigDecimal totalAmount = subtotalAmount.subtract(appliedVoucher.discountAmount());
        if (totalAmount.signum() > 0) {
            walletService.chargeForParking(
                    user,
                    totalAmount,
                    String.format(
                            "Thanh toán booking #%d - slot %s%s",
                            booking.getId(),
                            booking.getParkingSlot().getSlotName(),
                            appliedVoucher.code() != null ? " | voucher " + appliedVoucher.code() : ""));
        }

        booking.setStatus(Booking.BookingStatus.COMPLETED);
        booking.setCheckOutTime(checkOutTime);
        booking.setTotalAmount(totalAmount);
        booking.setDiscountAmount(appliedVoucher.discountAmount());
        booking.setAppliedVoucherCode(appliedVoucher.code());

        ParkingSlot slot = booking.getParkingSlot();
        slot.setStatus("AVAILABLE");
        parkingSlotRepository.save(slot);
        bookingRepository.save(booking);

        webSocketController.sendSlotUpdate(toSlotResponse(slot));

        String voucherMessage = appliedVoucher.code() != null
                ? String.format(" Đã áp dụng voucher %s, giảm %s VND.",
                        appliedVoucher.code(), appliedVoucher.discountAmount().toPlainString())
                : "";

        String checkoutMessage = totalAmount.signum() > 0
                ? String.format("Check-out thành công. Đã trừ %s VND từ ví.%s %s",
                        totalAmount.toPlainString(), voucherMessage, pricingResult.note())
                : "Check-out thành công. " + pricingResult.note();

        log.info("🏁 Check-out: Booking #{} → Slot [{}] AVAILABLE | total={}",
                bookingId, slot.getSlotName(), totalAmount);
        return toResponse(booking, checkoutMessage);
    }

    /**
     * Hủy booking (user tự hủy - chỉ được hủy khi PENDING)
     */
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findByIdAndUser_Email(bookingId, userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));

        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể hủy booking ở trạng thái PENDING");
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);

        ParkingSlot slot = booking.getParkingSlot();
        slot.setStatus("AVAILABLE");
        parkingSlotRepository.save(slot);
        bookingRepository.save(booking);

        // WebSocket broadcast (Tech Key #5)
        webSocketController.sendSlotUpdate(toSlotResponse(slot));

        log.info("❌ Booking #{} bị hủy bởi user [{}], slot [{}] trả về AVAILABLE",
                bookingId, userEmail, slot.getSlotName());
        return toResponse(booking, "Đã hủy booking thành công. Slot được trả về trạng thái trống.");
    }

    // =================== HELPER METHODS ===================
    private ParkingSlotResponse toSlotResponse(ParkingSlot slot) {
        return ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .type(slot.getType())
                .status(slot.getStatus())
                .pricePerHour(slot.getPricePerHour())
                .version(slot.getVersion())
                .build();
    }

    private BookingResponse toResponse(Booking booking, String message) {
        ParkingSlot slot = booking.getParkingSlot();
        User user = booking.getUser();
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .userId(user.getId())
                .userFullName(user.getFullName())
                .slotId(slot.getId())
                .slotName(slot.getSlotName())
                .slotType(slot.getType())
                .status(booking.getStatus().name())
                .bookingTime(booking.getBookingTime())
                .expiryTime(booking.getExpiryTime())
                .checkInTime(booking.getCheckInTime())
                .checkOutTime(booking.getCheckOutTime())
                .totalAmount(booking.getTotalAmount())
                                .discountAmount(booking.getDiscountAmount())
                                .appliedVoucherCode(booking.getAppliedVoucherCode())
                                .vehiclePlate(booking.getVehiclePlate())
                .qrCodeBase64(booking.getQrCodeBase64())
                .message(message)
                .build();
    }

        private void validateBookingQrSignature(Booking booking) {
                String qrContent = qrCodeService.buildBookingPayload(
                                booking.getId(),
                                booking.getUser().getId(),
                                booking.getParkingSlot().getSlotName(),
                                booking.getBookingTime(),
                                booking.getVehiclePlate());
                if (!qrCodeService.verifySignature(qrContent, booking.getQrSignature())) {
                        throw new RuntimeException("QR booking không hợp lệ hoặc đã bị chỉnh sửa.");
                }
        }

        private String normalizeVehiclePlate(String vehiclePlate) {
                if (vehiclePlate == null || vehiclePlate.isBlank()) {
                        return null;
                }
                return vehiclePlate.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        }
}
