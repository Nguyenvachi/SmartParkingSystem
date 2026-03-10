package com.parking.smartparking.service;

import java.math.BigDecimal;
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

    @Test
    void shouldRejectBookingWhenOptimisticLockConflictOccurs() {
        BookingService bookingService = createService();
        User user = User.builder()
                .id(1L)
                .email("race@test.com")
                .fullName("Race Test")
                .password("secret")
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

    private BookingService createService() {
        return new BookingService(
                bookingRepository,
                parkingSlotRepository,
                userRepository,
                qrCodeService,
                webSocketController,
                pricingService,
                walletService,
                voucherService);
    }
}
