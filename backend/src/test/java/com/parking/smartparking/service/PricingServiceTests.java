package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;

class PricingServiceTests {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService();
        ReflectionTestUtils.setField(pricingService, "dayStartHour", 6);
        ReflectionTestUtils.setField(pricingService, "nightStartHour", 18);
        ReflectionTestUtils.setField(pricingService, "nightMultiplier", new BigDecimal("1.5"));
        ReflectionTestUtils.setField(pricingService, "longStayThresholdHours", 12L);
        ReflectionTestUtils.setField(pricingService, "longStayMultiplier", new BigDecimal("1.25"));
    }

    @Test
    void shouldCalculateDayAndNightPricing() {
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 17, 0));
        User user = User.builder()
                .email("pricing@test.com")
                .fullName("Pricing Test")
                .password("secret")
                .build();

        PricingService.PricingResult result = pricingService.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 19, 0));

        assertEquals(new BigDecimal("12500.00"), result.totalAmount());
        assertTrue(result.note().contains("Tính phí động"));
    }

    @Test
    void shouldChargeOnlyAfterMembershipExpiry() {
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 17, 0));
        User user = User.builder()
                .email("member@test.com")
                .fullName("Member Test")
                .password("secret")
                .membershipPlan(User.MembershipPlan.MONTHLY)
                .membershipExpiry(LocalDateTime.of(2026, 3, 10, 18, 0))
                .build();

        PricingService.PricingResult result = pricingService.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 19, 0));

        assertEquals(new BigDecimal("7500.00"), result.totalAmount());
        assertTrue(result.note().contains("Vé tháng miễn phí đến"));
    }

    @Test
    void shouldReturnZeroWhenMembershipCoversFullStay() {
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 9, 0));
        User user = User.builder()
                .email("member-full@test.com")
                .fullName("Member Full Test")
                .password("secret")
                .membershipPlan(User.MembershipPlan.MONTHLY)
                .membershipExpiry(LocalDateTime.of(2026, 3, 11, 9, 0))
                .build();

        PricingService.PricingResult result = pricingService.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 10, 0));

        assertEquals(new BigDecimal("0.00"), result.totalAmount());
        assertTrue(result.note().contains("Vé tháng còn hiệu lực"));
    }

    private Booking createBooking(LocalDateTime checkInTime) {
        ParkingSlot slot = ParkingSlot.builder()
                .slotName("A01")
                .type("SEDAN")
                .pricePerHour(new BigDecimal("5000"))
                .status("OCCUPIED")
                .build();

        return Booking.builder()
                .parkingSlot(slot)
                .status(Booking.BookingStatus.CHECKED_IN)
                .checkInTime(checkInTime)
                .build();
    }
}