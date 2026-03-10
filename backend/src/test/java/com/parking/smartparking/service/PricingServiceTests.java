package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;

class PricingServiceTests {

    @Test
    void shouldCalculateDayAndNightPricing() {
        PricingService service = createPricingService();
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 17, 0));
        User user = User.builder()
                .email("pricing@test.com")
                .fullName("Pricing Test")
                .password("secret")
                .build();

        PricingService.PricingResult result = service.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 19, 0));

        assertEquals(new BigDecimal("12500.00"), result.totalAmount());
        assertTrue(result.note().contains("Tính phí động"));
    }

    @Test
    void shouldChargeOnlyAfterMembershipExpiry() {
        PricingService service = createPricingService();
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 17, 0));
        User user = User.builder()
                .email("member@test.com")
                .fullName("Member Test")
                .password("secret")
                .membershipPlan(User.MembershipPlan.MONTHLY)
                .membershipExpiry(LocalDateTime.of(2026, 3, 10, 18, 0))
                .build();

        PricingService.PricingResult result = service.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 19, 0));

        assertEquals(new BigDecimal("7500.00"), result.totalAmount());
        assertTrue(result.note().contains("Vé tháng miễn phí đến"));
    }

    @Test
    void shouldReturnZeroWhenMembershipCoversFullStay() {
        PricingService service = createPricingService();
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 9, 0));
        User user = User.builder()
                .email("member-full@test.com")
                .fullName("Member Full Test")
                .password("secret")
                .membershipPlan(User.MembershipPlan.MONTHLY)
                .membershipExpiry(LocalDateTime.of(2026, 3, 11, 9, 0))
                .build();

        PricingService.PricingResult result = service.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 10, 0));

        assertEquals(new BigDecimal("0.00"), result.totalAmount());
        assertTrue(result.note().contains("Vé tháng còn hiệu lực"));
    }

    @Test
    void shouldSplitPriceAtDayNightBoundary() {
        PricingService service = createPricingService();
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 17, 30));
        User user = User.builder()
                .email("boundary@test.com")
                .fullName("Boundary Test")
                .password("secret")
                .build();

        PricingService.PricingResult result = service.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 18, 30));

        assertEquals(new BigDecimal("6250.00"), result.totalAmount());
    }

    @Test
    void shouldApplyLongStayMultiplierOnlyAfterThreshold() {
        PricingService service = createPricingService();
        Booking booking = createBooking(LocalDateTime.of(2026, 3, 10, 6, 0));
        User user = User.builder()
                .email("longstay@test.com")
                .fullName("Long Stay Test")
                .password("secret")
                .build();

        PricingService.PricingResult result = service.calculateCheckoutAmount(
                booking,
                user,
                LocalDateTime.of(2026, 3, 10, 19, 0));

        assertEquals(new BigDecimal("69375.00"), result.totalAmount());
    }

    private PricingService createPricingService() {
        PricingService service = new PricingService();
        ReflectionTestUtils.setField(service, "dayStartHour", 6);
        ReflectionTestUtils.setField(service, "nightStartHour", 18);
        ReflectionTestUtils.setField(service, "nightMultiplier", new BigDecimal("1.5"));
        ReflectionTestUtils.setField(service, "longStayThresholdHours", 12L);
        ReflectionTestUtils.setField(service, "longStayMultiplier", new BigDecimal("1.25"));
        return service;
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
