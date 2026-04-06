package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.ApplyVoucherRequest;
import com.parking.smartparking.dto.request.BookingRequest;
import com.parking.smartparking.dto.request.CheckoutRequest;
import com.parking.smartparking.dto.response.BookingResponse;
import com.parking.smartparking.dto.response.CheckoutPreviewResponse;
import com.parking.smartparking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * BookingController - REST API cho chức năng đặt chỗ
 *
 * Base URL: /api/bookings Tất cả endpoints đều yêu cầu JWT token
 * (Authorization: Bearer <token>)
 *
 * Endpoints: - POST /api/bookings : Tạo booking mới (Tech #1 Optimistic
 * Locking) - GET /api/bookings : Xem lịch sử booking của user - GET
 * /api/bookings/{id} : Xem chi tiết 1 booking (có QR Code) - POST
 * /api/bookings/{id}/checkin : Check-in vào bãi - DELETE /api/bookings/{id} :
 * Hủy booking
 *
 * Cách lấy email user: Authentication.getName() → email từ JWT subject
 *
 * Quan hệ: - File Con (Dependency): BookingService.java (xử lý logic) - DTOs sử
 * dụng: BookingRequest.java (input), BookingResponse.java (output)
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * API Đặt chỗ mới
     *
     * Request: POST /api/bookings Authorization: Bearer <jwt_token>
     * Body: { "slotId": 1 }
     *
     * Response (201 Created): { "bookingId": 1, "slotName": "A01", "status":
     * "PENDING", "expiryTime": "...", "qrCodeBase64": "iVBORw0KGgo...",
     * "message": "Đặt chỗ thành công! Vui lòng vào bãi trong 15 phút." }
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            Authentication authentication) {
        String email = authentication.getName(); // Email từ JWT
        BookingResponse response = bookingService.createBooking(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * API Xem lịch sử booking của user hiện tại
     *
     * Request: GET /api/bookings Authorization: Bearer <jwt_token>
     *
     * Response (200 OK): Danh sách booking của user, mới nhất trước
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getUserBookings(Authentication authentication) {
        String email = authentication.getName();
        List<BookingResponse> bookings = bookingService.getUserBookings(email);
        return ResponseEntity.ok(bookings);
    }

    /**
     * API Xem chi tiết 1 booking (bao gồm QR Code)
     *
     * Request: GET /api/bookings/1 Authorization: Bearer <jwt_token>
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        BookingResponse booking = bookingService.getBookingById(id, email);
        return ResponseEntity.ok(booking);
    }

    /**
     * User applies a voucher to a running booking before reaching the gate.
     * Staff will then checkout without typing voucher at the gate.
     */
    @PutMapping("/{id}/apply-voucher")
    public ResponseEntity<BookingResponse> applyVoucher(
            @PathVariable Long id,
            @RequestBody(required = false) ApplyVoucherRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        BookingResponse response = bookingService.applyVoucher(id, email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Staff-only checkout preview (no state changes).
     */
    @GetMapping("/{id}/checkout-preview")
    public ResponseEntity<CheckoutPreviewResponse> checkoutPreview(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(bookingService.previewCheckoutAsStaff(id, email));
    }

    /**
     * API Check-in (Vào bãi xe)
     *
     * Request: POST /api/bookings/1/checkin Authorization: Bearer <jwt_token>
     *
     * Điều kiện: Booking phải ở PENDING và chưa hết 15 phút Kết quả: status →
     * CHECKED_IN, slot → OCCUPIED
     */
    @PostMapping("/{id}/checkin")
    public ResponseEntity<BookingResponse> checkIn(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        // Staff-only gate operation (barrier/guard). Authorization is enforced in SecurityConfig.
        BookingResponse response = bookingService.checkInAsStaff(id, email);
        return ResponseEntity.ok(response);
    }

    /**
     * API Check-out (Ra bãi xe + thanh toán ví)
     */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<BookingResponse> checkOut(
            @PathVariable Long id,
            @RequestBody(required = false) CheckoutRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        // Staff-only gate operation (barrier/guard). Authorization is enforced in SecurityConfig.
        BookingResponse response = bookingService.checkOutAsStaff(id, email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * API Hủy booking (User tự hủy)
     *
     * Request: DELETE /api/bookings/1 Authorization: Bearer <jwt_token>
     *
     * Điều kiện: Chỉ hủy được khi status = PENDING Kết quả: status → CANCELLED,
     * slot → AVAILABLE
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        BookingResponse response = bookingService.cancelBooking(id, email);
        return ResponseEntity.ok(response);
    }
}
