package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;

@Service
public class PricingService {

    @Value("${app.pricing.day-start-hour:6}")
    private int dayStartHour;

    @Value("${app.pricing.night-start-hour:18}")
    private int nightStartHour;

    @Value("${app.pricing.night-multiplier:1.5}")
    private BigDecimal nightMultiplier;

    @Value("${app.pricing.long-stay-threshold-hours:12}")
    private long longStayThresholdHours;

    @Value("${app.pricing.long-stay-multiplier:1.25}")
    private BigDecimal longStayMultiplier;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public PricingResult calculateCheckoutAmount(Booking booking, User user, LocalDateTime checkOutTime) {
        LocalDateTime checkInTime = booking.getCheckInTime();
        if (checkInTime == null) {
            throw new RuntimeException("Booking chưa có thời gian check-in để tính phí.");
        }

        if (!checkOutTime.isAfter(checkInTime)) {
            return new PricingResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), checkOutTime,
                    "Phiên gửi xe chưa phát sinh thời lượng tính phí.");
        }

        LocalDateTime chargeableStart = checkInTime;
        String note = "Tính phí động theo khung giờ ngày/đêm.";

        if (user.getMembershipPlan() == User.MembershipPlan.MONTHLY
                && user.getMembershipExpiry() != null
                && user.getMembershipExpiry().isAfter(checkInTime)) {
            if (!user.getMembershipExpiry().isBefore(checkOutTime)) {
                return new PricingResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), checkOutTime,
                        "Vé tháng còn hiệu lực trong toàn bộ lượt gửi xe, không phát sinh phí.");
            }

            chargeableStart = user.getMembershipExpiry();
            note = "Vé tháng miễn phí đến " + DATE_TIME_FORMATTER.format(user.getMembershipExpiry())
                    + ", chỉ tính phí phần thời gian sau khi vé tháng hết hạn.";
        }

        BigDecimal totalAmount = calculateDynamicPrice(booking.getParkingSlot(), chargeableStart, checkOutTime);
        return new PricingResult(totalAmount, chargeableStart, note);
    }

    private BigDecimal calculateDynamicPrice(ParkingSlot slot, LocalDateTime chargeableStart, LocalDateTime chargeableEnd) {
        if (!chargeableEnd.isAfter(chargeableStart)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        LocalDateTime cursor = chargeableStart;
        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime longStayBoundary = chargeableStart.plusHours(longStayThresholdHours);

        while (cursor.isBefore(chargeableEnd)) {
            LocalDateTime next = chargeableEnd;

            LocalDateTime nextPricingBoundary = getNextPricingBoundary(cursor);
            if (nextPricingBoundary.isAfter(cursor) && nextPricingBoundary.isBefore(next)) {
                next = nextPricingBoundary;
            }

            if (cursor.isBefore(longStayBoundary) && longStayBoundary.isBefore(next)) {
                next = longStayBoundary;
            }

            long minutes = Duration.between(cursor, next).toMinutes();
            BigDecimal hourFraction = BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            BigDecimal chunkPrice = slot.getPricePerHour().multiply(hourFraction);

            if (isNightWindow(cursor)) {
                chunkPrice = chunkPrice.multiply(nightMultiplier);
            }

            if (!cursor.isBefore(longStayBoundary)) {
                chunkPrice = chunkPrice.multiply(longStayMultiplier);
            }

            total = total.add(chunkPrice);
            cursor = next;
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isNightWindow(LocalDateTime time) {
        int hour = time.getHour();
        return hour < dayStartHour || hour >= nightStartHour;
    }

    private LocalDateTime getNextPricingBoundary(LocalDateTime time) {
        LocalDateTime dayBoundary = time.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime nightBoundary = time.withHour(nightStartHour).withMinute(0).withSecond(0).withNano(0);

        if (time.isBefore(dayBoundary)) {
            return dayBoundary;
        }

        if (time.isBefore(nightBoundary)) {
            return nightBoundary;
        }

        return dayBoundary.plusDays(1);
    }

    public record PricingResult(BigDecimal totalAmount, LocalDateTime chargeableFrom, String note) {

    }
}
