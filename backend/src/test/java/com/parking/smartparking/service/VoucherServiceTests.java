package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parking.smartparking.entity.Voucher;
import com.parking.smartparking.repository.VoucherRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unused"})
class VoucherServiceTests {

    @Mock
    private VoucherRepository voucherRepository;

    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherService(voucherRepository);
    }

    @Test
    void shouldApplyAndConsumeLastVoucherUse() {
        Voucher voucher = Voucher.builder()
                .code("FLASH1")
                .discountType(Voucher.DiscountType.FIXED)
                .discountValue(new BigDecimal("15000"))
                .minOrderAmount(new BigDecimal("15000"))
                .maxDiscountAmount(new BigDecimal("15000"))
                .remainingUses(1)
                .active(true)
                .validUntil(LocalDateTime.now().plusDays(2))
                .description("Last use")
                .build();

        when(voucherRepository.findByCodeForUpdate("FLASH1")).thenReturn(Optional.of(voucher));

        VoucherService.AppliedVoucher appliedVoucher = voucherService.applyVoucherToAmount(
                "FLASH1",
                new BigDecimal("50000"));

        assertEquals(new BigDecimal("15000.00"), appliedVoucher.discountAmount());
        assertEquals(0, voucher.getRemainingUses());
        assertEquals(false, voucher.getActive());
        verify(voucherRepository).save(any(Voucher.class));
    }

    @Test
    void shouldRejectVoucherOnSecondAttemptAfterLastUseWasConsumed() {
        Voucher voucher = Voucher.builder()
                .code("FLASH1")
                .discountType(Voucher.DiscountType.FIXED)
                .discountValue(new BigDecimal("15000"))
                .minOrderAmount(new BigDecimal("15000"))
                .maxDiscountAmount(new BigDecimal("15000"))
                .remainingUses(1)
                .active(true)
                .validUntil(LocalDateTime.now().plusDays(2))
                .description("Last use")
                .build();

        when(voucherRepository.findByCodeForUpdate("FLASH1")).thenReturn(Optional.of(voucher));

        voucherService.applyVoucherToAmount("FLASH1", new BigDecimal("50000"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> voucherService.applyVoucherToAmount("FLASH1", new BigDecimal("50000")));

        assertEquals("Voucher FLASH1 hiện không còn khả dụng.", exception.getMessage());
    }

    @Test
    void shouldRejectExpiredVoucher() {
        Voucher voucher = Voucher.builder()
                .code("OLD")
                .discountType(Voucher.DiscountType.PERCENT)
                .discountValue(new BigDecimal("10"))
                .remainingUses(5)
                .active(true)
                .validUntil(LocalDateTime.now().minusMinutes(1))
                .description("Expired")
                .build();

        when(voucherRepository.findByCodeForUpdate("OLD")).thenReturn(Optional.of(voucher));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> voucherService.applyVoucherToAmount("OLD", new BigDecimal("50000")));

        assertEquals("Voucher OLD đã hết hạn.", exception.getMessage());
    }
}
