package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.Invoice;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.InvoiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter INVOICE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InvoiceRepository invoiceRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@smartparking.local}")
    private String mailFrom;

    @Transactional
    public Invoice createAndMaybeSendForCompletedBooking(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("booking không được null");
        }
        if (booking.getId() == null) {
            throw new IllegalArgumentException("bookingId không hợp lệ");
        }
        if (booking.getStatus() != Booking.BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Chỉ tạo hóa đơn khi booking COMPLETED.");
        }

        Invoice invoice = invoiceRepository.findByBooking_Id(booking.getId())
                .orElseGet(() -> invoiceRepository.save(buildInvoiceSnapshot(booking)));

        // Best-effort email sending (do not fail checkout)
        trySendInvoiceEmail(invoice);
        return invoice;
    }

    private Invoice buildInvoiceSnapshot(Booking booking) {
        User user = booking.getUser();
        ParkingSlot slot = booking.getParkingSlot();

        BigDecimal total = booking.getTotalAmount();
        BigDecimal discount = booking.getDiscountAmount() != null ? booking.getDiscountAmount() : BigDecimal.ZERO;
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        BigDecimal subtotal = total.add(discount);

        String invoiceNumber = "INV-" + booking.getId() + "-" + LocalDateTime.now().format(INVOICE_TS);

        return Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .booking(booking)
                .userEmail(user != null ? user.getEmail() : "")
                .userFullName(user != null ? user.getFullName() : null)
                .slotName(slot != null ? slot.getSlotName() : null)
                .branchCode(slot != null ? slot.getBranchCode() : null)
                .vehiclePlate(booking.getVehiclePlate())
                .checkInTime(booking.getCheckInTime())
                .checkOutTime(booking.getCheckOutTime())
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .totalAmount(total)
                .voucherCode(booking.getAppliedVoucherCode())
                .build();
    }

    private void trySendInvoiceEmail(Invoice invoice) {
        if (!mailEnabled) {
            return;
        }
        if (invoice == null) {
            return;
        }
        if (invoice.getEmailSentAt() != null) {
            return;
        }

        String to = (invoice.getUserEmail() != null) ? invoice.getUserEmail().trim() : "";
        if (to.isBlank()) {
            invoice.setEmailSendError("Không có email người nhận.");
            invoiceRepository.save(invoice);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("[SmartParking] Hóa đơn cho booking #" + invoice.getBooking().getId());
            message.setText(renderInvoiceText(invoice));

            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                invoice.setEmailSendError("Mail sender chưa được cấu hình.");
                invoiceRepository.save(invoice);
                return;
            }
            mailSender.send(message);

            invoice.setEmailSentTo(to);
            invoice.setEmailSentAt(LocalDateTime.now());
            invoice.setEmailSendError(null);
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            log.warn("Failed to send invoice email for booking #{}: {}",
                    invoice.getBooking() != null ? invoice.getBooking().getId() : null,
                    e.getMessage());
            invoice.setEmailSendError(e.getMessage());
            invoiceRepository.save(invoice);
        }
    }

    private String renderInvoiceText(Invoice invoice) {
        StringBuilder sb = new StringBuilder();
        sb.append("HÓA ĐƠN SMART PARKING\n");
        sb.append("Số hóa đơn: ").append(invoice.getInvoiceNumber()).append("\n");
        sb.append("Booking #: ").append(invoice.getBooking().getId()).append("\n\n");

        if (invoice.getUserFullName() != null && !invoice.getUserFullName().isBlank()) {
            sb.append("Khách hàng: ").append(invoice.getUserFullName()).append("\n");
        }
        sb.append("Email: ").append(invoice.getUserEmail()).append("\n");
        if (invoice.getBranchCode() != null) {
            sb.append("Chi nhánh: ").append(invoice.getBranchCode()).append("\n");
        }
        if (invoice.getSlotName() != null) {
            sb.append("Slot: ").append(invoice.getSlotName()).append("\n");
        }
        if (invoice.getVehiclePlate() != null) {
            sb.append("Biển số: ").append(invoice.getVehiclePlate()).append("\n");
        }
        if (invoice.getCheckInTime() != null) {
            sb.append("Check-in: ").append(invoice.getCheckInTime()).append("\n");
        }
        if (invoice.getCheckOutTime() != null) {
            sb.append("Check-out: ").append(invoice.getCheckOutTime()).append("\n");
        }

        sb.append("\nTạm tính: ").append(safe(invoice.getSubtotalAmount())).append(" VND\n");
        sb.append("Giảm giá: ").append(safe(invoice.getDiscountAmount())).append(" VND\n");
        if (invoice.getVoucherCode() != null && !invoice.getVoucherCode().isBlank()) {
            sb.append("Voucher: ").append(invoice.getVoucherCode()).append("\n");
        }
        sb.append("Tổng thanh toán: ").append(safe(invoice.getTotalAmount())).append(" VND\n");

        sb.append("\nCảm ơn bạn đã sử dụng Smart Parking.");
        return sb.toString();
    }

    private String safe(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
