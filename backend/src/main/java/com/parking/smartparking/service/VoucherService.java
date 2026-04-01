package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.response.VoucherResponse;
import com.parking.smartparking.entity.Voucher;
import com.parking.smartparking.repository.VoucherRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    @Value("${app.voucher.seed-enabled:true}")
    private boolean seedEnabled;

    @PostConstruct
    @Transactional
    public void seedDefaultVouchers() {
        if (!seedEnabled || voucherRepository.count() > 0) {
            return;
        }

        voucherRepository.saveAll(List.of(
                Voucher.builder()
                        .code("WELCOME50")
                        .discountType(Voucher.DiscountType.PERCENT)
                        .discountValue(new BigDecimal("50"))
                        .minOrderAmount(new BigDecimal("20000"))
                        .maxDiscountAmount(new BigDecimal("30000"))
                        .remainingUses(10)
                        .active(true)
                        .validUntil(LocalDateTime.now().plusMonths(2))
                        .description("Giảm 50% cho lượt checkout đầu, tối đa 30.000 VND")
                        .build(),
                Voucher.builder()
                        .code("FLASH1")
                        .discountType(Voucher.DiscountType.FIXED)
                        .discountValue(new BigDecimal("15000"))
                        .minOrderAmount(new BigDecimal("15000"))
                        .maxDiscountAmount(new BigDecimal("15000"))
                        .remainingUses(1)
                        .active(true)
                        .validUntil(LocalDateTime.now().plusDays(30))
                        .description("Voucher tranh chấp còn 1 lượt cuối để demo locking")
                        .build()));
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> getAvailableVouchers() {
        return voucherRepository.findByActiveTrueAndRemainingUsesGreaterThanAndValidUntilAfterOrderByValidUntilAsc(
                0,
                LocalDateTime.now())
                .stream()
                .map(voucher -> VoucherResponse.builder()
                .code(voucher.getCode())
                .discountType(voucher.getDiscountType().name())
                .discountValue(voucher.getDiscountValue())
                .minOrderAmount(voucher.getMinOrderAmount())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .remainingUses(voucher.getRemainingUses())
                .validUntil(voucher.getValidUntil())
                .description(voucher.getDescription())
                .build())
                .toList();
    }

    @Transactional
    public AppliedVoucher applyVoucherToAmount(String voucherCode, BigDecimal subtotalAmount) {
        if (voucherCode == null || voucherCode.isBlank() || subtotalAmount == null || subtotalAmount.signum() <= 0) {
            return AppliedVoucher.empty();
        }

        Voucher voucher = voucherRepository.findByCodeForUpdate(voucherCode.trim())
                .orElseThrow(() -> new RuntimeException("Voucher " + voucherCode + " không tồn tại hoặc đã hết hạn."));

        validateVoucher(voucher, subtotalAmount);

        BigDecimal discountAmount = calculateDiscount(voucher, subtotalAmount)
                .min(subtotalAmount)
                .setScale(2, RoundingMode.HALF_UP);

        voucher.setRemainingUses(voucher.getRemainingUses() - 1);
        if (voucher.getRemainingUses() <= 0) {
            voucher.setActive(false);
        }
        voucherRepository.save(voucher);

        return new AppliedVoucher(voucher.getCode(), discountAmount);
    }

    private void validateVoucher(Voucher voucher, BigDecimal subtotalAmount) {
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            throw new RuntimeException("Voucher " + voucher.getCode() + " hiện không còn khả dụng.");
        }

        if (voucher.getValidUntil() != null && voucher.getValidUntil().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Voucher " + voucher.getCode() + " đã hết hạn.");
        }

        if (voucher.getRemainingUses() == null || voucher.getRemainingUses() <= 0) {
            throw new RuntimeException("Voucher " + voucher.getCode() + " đã hết lượt sử dụng.");
        }

        if (voucher.getMinOrderAmount() != null && subtotalAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new RuntimeException("Đơn checkout chưa đạt mức tối thiểu để áp dụng voucher " + voucher.getCode() + ".");
        }
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotalAmount) {
        BigDecimal discountAmount = voucher.getDiscountType() == Voucher.DiscountType.PERCENT
                ? subtotalAmount.multiply(voucher.getDiscountValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : voucher.getDiscountValue();

        if (voucher.getMaxDiscountAmount() != null) {
            discountAmount = discountAmount.min(voucher.getMaxDiscountAmount());
        }

        return discountAmount.max(BigDecimal.ZERO);
    }

    public record AppliedVoucher(String code, BigDecimal discountAmount) {

        public static AppliedVoucher empty() {
            return new AppliedVoucher(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }
}
