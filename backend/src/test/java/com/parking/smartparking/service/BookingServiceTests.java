package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.request.BookingRequest;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BookingServiceTests {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingSlotRepository parkingSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QRCodeService qrCodeService;

    @Mock
    private WebSocketController webSocketController;

    @Mock
    private PricingService pricingService;

    @Mock
    private WalletService walletService;

    @Mock
    private VoucherService voucherService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private BlacklistService blacklistService;

    @Test
    void shouldRejectBookingWhenOptimisticLockConflictOccurs() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("race@test.com")
                .fullName("Race Test")
                .password("secret")
                .walletBalance(new BigDecimal("5000"))
                .build();

        ParkingSlot slot = ParkingSlot.builder()
                .id(5L)
                .slotName("A01")
                .type("SEDAN")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("5000"))
                .version(1L)
                .build();

        BookingRequest request = new BookingRequest();
        request.setSlotId(5L);

        when(userRepository.findByEmail("race@test.com")).thenReturn(Optional.of(user));
        when(bookingRepository.existsByUser_EmailAndStatusIn(
                eq("race@test.com"),
                eq(List.of(com.parking.smartparking.entity.Booking.BookingStatus.PENDING,
                        com.parking.smartparking.entity.Booking.BookingStatus.CHECKED_IN))))
                .thenReturn(false);
        when(parkingSlotRepository.findById(5L)).thenReturn(Optional.of(slot));
        when(bookingRepository.existsByUser_EmailAndParkingSlot_IdAndStatusIn(
                eq("race@test.com"),
                eq(5L),
                eq(List.of(com.parking.smartparking.entity.Booking.BookingStatus.PENDING,
                        com.parking.smartparking.entity.Booking.BookingStatus.CHECKED_IN))))
                .thenReturn(false);
        when(parkingSlotRepository.saveAndFlush(any(ParkingSlot.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ParkingSlot.class, 5L));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.createBooking("race@test.com", request));

        assertEquals("Slot A01 vừa được người khác đặt mất! Vui lòng chọn slot khác.", exception.getMessage());
        verify(webSocketController, never()).sendSlotUpdate(any());
    }

    @Test
    void shouldRejectBookingWhenUserIsAdmin() {
        BookingService bookingService = createService();
        User admin = User.builder()
                .id(99L)
                .email("admin@test.com")
                .fullName("Admin")
                .password("secret")
                .role(User.Role.ROLE_ADMIN)
                .walletBalance(new BigDecimal("999999"))
                .build();

        BookingRequest request = new BookingRequest();
        request.setSlotId(1L);

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.createBooking("admin@test.com", request));

        assertEquals("Tài khoản ADMIN không được phép đặt chỗ.", exception.getMessage());
        verify(parkingSlotRepository, never()).findById(any());
        verify(parkingSlotRepository, never()).saveAndFlush(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectBookingWhenUserAlreadyHasActiveBooking() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .fullName("User")
                .password("secret")
                .walletBalance(new BigDecimal("5000"))
                .build();

        BookingRequest request = new BookingRequest();
        request.setSlotId(1L);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(bookingRepository.existsByUser_EmailAndStatusIn(
                eq("user@test.com"),
                eq(List.of(com.parking.smartparking.entity.Booking.BookingStatus.PENDING,
                        com.parking.smartparking.entity.Booking.BookingStatus.CHECKED_IN))))
                .thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.createBooking("user@test.com", request));

        assertEquals("Bạn đang có 1 booking đang hoạt động. Vui lòng hoàn tất hoặc hủy trước khi đặt mới.",
                exception.getMessage());
        verify(parkingSlotRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectBookingWhenWalletBalanceIsInsufficient() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("poor@test.com")
                .fullName("Poor User")
                .password("secret")
                .walletBalance(new BigDecimal("1000"))
                .build();

        ParkingSlot slot = ParkingSlot.builder()
                .id(7L)
                .slotName("C03")
                .type("SEDAN")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("5000"))
                .version(1L)
                .build();

        BookingRequest request = new BookingRequest();
        request.setSlotId(7L);

        when(userRepository.findByEmail("poor@test.com")).thenReturn(Optional.of(user));
        when(bookingRepository.existsByUser_EmailAndStatusIn(
                eq("poor@test.com"),
                eq(List.of(com.parking.smartparking.entity.Booking.BookingStatus.PENDING,
                        com.parking.smartparking.entity.Booking.BookingStatus.CHECKED_IN))))
                .thenReturn(false);
        when(parkingSlotRepository.findById(7L)).thenReturn(Optional.of(slot));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.createBooking("poor@test.com", request));

        assertEquals("Số dư ví không đủ để đặt slot này. Vui lòng nạp thêm tiền.", exception.getMessage());
        verify(parkingSlotRepository, never()).saveAndFlush(any());
        verify(bookingRepository, never()).save(any());
        verify(webSocketController, never()).sendSlotUpdate(any());
    }

    @Test
    void shouldRejectCheckInWhenQrSignatureIsInvalid() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .fullName("User Test")
                .password("secret")
                .build();
        ParkingSlot slot = ParkingSlot.builder()
                .id(2L)
                .slotName("A01")
                .type("SEDAN")
                .status("RESERVED")
                .branchCode("MAIN")
                .pricePerHour(new BigDecimal("5000"))
                .build();
        Booking booking = Booking.builder()
                .id(10L)
                .user(user)
                .parkingSlot(slot)
                .status(Booking.BookingStatus.PENDING)
                .bookingTime(LocalDateTime.now().minusMinutes(5))
                .expiryTime(LocalDateTime.now().plusMinutes(10))
                .qrSignature("tampered")
                .vehiclePlate("29A12345")
                .build();

        when(bookingRepository.findByIdAndUser_Email(10L, "user@test.com")).thenReturn(Optional.of(booking));
        when(qrCodeService.buildBookingPayload(any(), any(), any(), any(), any())).thenReturn("payload");
        when(qrCodeService.verifySignature("payload", "tampered")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.checkIn(10L, "user@test.com"));

        assertEquals("QR booking không hợp lệ hoặc đã bị chỉnh sửa.", exception.getMessage());
        verify(blacklistService, never()).assertVehicleAllowed(any(), any());
    }

    @Test
    void shouldRejectCheckInWhenVehicleIsBlacklisted() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .fullName("User Test")
                .password("secret")
                .build();
        ParkingSlot slot = ParkingSlot.builder()
                .id(3L)
                .slotName("B02")
                .type("SUV")
                .status("RESERVED")
                .branchCode("HCM")
                .pricePerHour(new BigDecimal("7000"))
                .build();
        Booking booking = Booking.builder()
                .id(11L)
                .user(user)
                .parkingSlot(slot)
                .status(Booking.BookingStatus.PENDING)
                .bookingTime(LocalDateTime.now().minusMinutes(2))
                .expiryTime(LocalDateTime.now().plusMinutes(10))
                .qrSignature("valid")
                .vehiclePlate("51F12345")
                .build();

        when(bookingRepository.findByIdAndUser_Email(11L, "user@test.com")).thenReturn(Optional.of(booking));
        when(qrCodeService.buildBookingPayload(any(), any(), any(), any(), any())).thenReturn("payload");
        when(qrCodeService.verifySignature("payload", "valid")).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Xe biển số 51F12345 đang nằm trong blacklist: Vi phạm."))
                .when(blacklistService).assertVehicleAllowed("51F12345", "HCM");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookingService.checkIn(11L, "user@test.com"));

        assertEquals("Xe biển số 51F12345 đang nằm trong blacklist: Vi phạm.", exception.getMessage());
        verify(parkingSlotRepository, never()).save(any(ParkingSlot.class));
    }

    private BookingService createService() {
        return new BookingService(
                bookingRepository,
                parkingSlotRepository,
                userRepository,
                qrCodeService,
                webSocketController,
                pricingService,
                walletService,
                voucherService,
                invoiceService,
                blacklistService);
    }
}
