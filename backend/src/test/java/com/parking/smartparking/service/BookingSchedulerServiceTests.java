package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BookingSchedulerServiceTests {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingSlotRepository parkingSlotRepository;

    @Mock
    private WebSocketController webSocketController;

    @Test
    void shouldCancelExpiredPendingBookingAndReleaseReservedSlot() {
        BookingSchedulerService bookingSchedulerService = createService();
        ParkingSlot slot = ParkingSlot.builder()
                .id(1L)
                .slotName("A01")
                .type("SEDAN")
                .status("RESERVED")
                .pricePerHour(new BigDecimal("5000"))
                .version(3L)
                .build();

        User user = User.builder()
                .id(10L)
                .fullName("Scheduler Test")
                .email("scheduler@test.com")
                .password("secret")
                .build();

        Booking booking = Booking.builder()
                .id(20L)
                .user(user)
                .parkingSlot(slot)
                .status(Booking.BookingStatus.PENDING)
                .expiryTime(LocalDateTime.now().minusMinutes(1))
                .build();

        when(bookingRepository.findByStatusAndExpiryTimeBefore(any(), any())).thenReturn(List.of(booking));

        bookingSchedulerService.cancelExpiredBookings();

        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("AVAILABLE", slot.getStatus());
        verify(parkingSlotRepository).save(slot);
        verify(webSocketController).sendSlotUpdate(any());
        verify(bookingRepository).saveAll(any());
    }

    @Test
    void shouldCancelExpiredBookingWithoutReleasingOccupiedSlot() {
        BookingSchedulerService bookingSchedulerService = createService();
        ParkingSlot slot = ParkingSlot.builder()
                .id(2L)
                .slotName("B02")
                .type("SUV")
                .status("OCCUPIED")
                .pricePerHour(new BigDecimal("7000"))
                .version(1L)
                .build();

        User user = User.builder()
                .id(11L)
                .fullName("Scheduler Occupied")
                .email("occupied@test.com")
                .password("secret")
                .build();

        Booking booking = Booking.builder()
                .id(21L)
                .user(user)
                .parkingSlot(slot)
                .status(Booking.BookingStatus.PENDING)
                .expiryTime(LocalDateTime.now().minusMinutes(2))
                .build();

        when(bookingRepository.findByStatusAndExpiryTimeBefore(any(), any())).thenReturn(List.of(booking));

        bookingSchedulerService.cancelExpiredBookings();

        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("OCCUPIED", slot.getStatus());
        verify(parkingSlotRepository, never()).save(slot);
        verify(webSocketController, never()).sendSlotUpdate(any());
        verify(bookingRepository).saveAll(any());
    }

    private BookingSchedulerService createService() {
        return new BookingSchedulerService(
                bookingRepository,
                parkingSlotRepository,
                webSocketController);
    }
}
