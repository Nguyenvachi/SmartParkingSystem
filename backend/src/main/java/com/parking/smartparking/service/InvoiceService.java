package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.Invoice;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.InvoiceRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter INVOICE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter INVOICE_HUMAN_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            log.debug("Invoice email skipped: app.mail.enabled=false");
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
            log.warn("Invoice email skipped: missing recipient email | invoiceId={} | bookingId={}",
                    invoice.getId(),
                    invoice.getBooking() != null ? invoice.getBooking().getId() : null);
            return;
        }

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                invoice.setEmailSendError("Mail sender chưa được cấu hình.");
                invoiceRepository.save(invoice);
                log.warn("Mail enabled but JavaMailSender is not available; check spring.mail.* configuration");
                return;
            }

            // Prefer HTML email; fallback to plain text if needed.
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(mailFrom);
                helper.setTo(to);
                helper.setSubject("[SmartParking] Hóa đơn cho booking #" + invoice.getBooking().getId());

                String htmlContent = renderInvoiceHtml(invoice);
                helper.setText(renderInvoiceText(invoice), htmlContent);

                mailSender.send(mimeMessage);

                invoice.setEmailSentTo(to);
                invoice.setEmailSentAt(LocalDateTime.now());
                invoice.setEmailSendError(null);
                invoiceRepository.save(invoice);
                return;
            } catch (Exception htmlEx) {
                log.warn("Failed to send HTML invoice email for booking #{}",
                        invoice.getBooking() != null ? invoice.getBooking().getId() : null,
                        htmlEx);
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("[SmartParking] Hóa đơn cho booking #" + invoice.getBooking().getId());
            message.setText(renderInvoiceText(invoice));
            mailSender.send(message);

            invoice.setEmailSentTo(to);
            invoice.setEmailSentAt(LocalDateTime.now());
            invoice.setEmailSendError(null);
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            log.warn("Failed to send invoice email for booking #{}",
                    invoice.getBooking() != null ? invoice.getBooking().getId() : null,
                    e);
            invoice.setEmailSendError(e.getMessage());
            invoiceRepository.save(invoice);
        }
    }

    private String renderInvoiceHtml(Invoice invoice) {
        Long bookingId = invoice.getBooking() != null ? invoice.getBooking().getId() : null;
        String bookingIdText = bookingId != null ? String.valueOf(bookingId) : "";
        String invoiceNumber = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "";

        String customerName = invoice.getUserFullName() != null ? invoice.getUserFullName().trim() : "";
        String customerEmail = invoice.getUserEmail() != null ? invoice.getUserEmail().trim() : "";
        String branch = invoice.getBranchCode() != null ? invoice.getBranchCode() : "";
        String slot = invoice.getSlotName() != null ? invoice.getSlotName() : "";
        String plate = invoice.getVehiclePlate() != null ? invoice.getVehiclePlate() : "";

        String checkIn = invoice.getCheckInTime() != null ? invoice.getCheckInTime().format(INVOICE_HUMAN_TS) : "";
        String checkOut = invoice.getCheckOutTime() != null ? invoice.getCheckOutTime().format(INVOICE_HUMAN_TS) : "";

        String subtotal = safe(invoice.getSubtotalAmount());
        String discount = safe(invoice.getDiscountAmount());
        String total = safe(invoice.getTotalAmount());
        String voucher = invoice.getVoucherCode() != null ? invoice.getVoucherCode().trim() : "";

        String customerLine = (customerName != null && !customerName.isBlank()) ? customerName : "(Không cung cấp)";
        String voucherRow = (voucher != null && !voucher.isBlank())
                ? ("""
                                                <tr>
                                                    <td style=\"padding:10px 0;color:#6b7280;font-size:13px;\">Voucher</td>
                                                    <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;font-weight:700;\">%s</td>
                                                </tr>
                                                """).formatted(voucher)
                : "";

        String template = """
                                <!doctype html>
                                <html lang=\"vi\">
                                    <head>
                                        <meta charset=\"utf-8\" />
                                        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                                        <title>Hóa đơn SmartParking</title>
                                    </head>
                                    <body style=\"margin:0;padding:0;background:#f6f9fc;\">
                                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f6f9fc;padding:24px 12px;\">
                                            <tr>
                                                <td align=\"center\">
                                                    <table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden;\">
                                                        <tr>
                                                            <td style=\"padding:18px 20px;background:#111827;\">
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:18px;line-height:22px;font-weight:800;color:#ffffff;\">Smart Parking</div>
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:16px;color:#9ca3af;margin-top:3px;\">Biên lai thanh toán điện tử</div>
                                                            </td>
                                                        </tr>

                                                        <tr>
                                                            <td style=\"padding:20px;\">
                                                                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"font-family:Arial,Helvetica,sans-serif;\">
                                                                    <tr>
                                                                        <td style=\"padding:0 0 8px 0;\">
                                                                            <div style=\"font-size:12px;line-height:16px;color:#6b7280;\">Số hóa đơn</div>
                                                                            <div style=\"font-size:16px;line-height:20px;color:#111827;font-weight:800;\">{{INVOICE_NUMBER}}</div>
                                                                        </td>
                                                                        <td style=\"padding:0 0 8px 0;text-align:right;\">
                                                                            <div style=\"font-size:12px;line-height:16px;color:#6b7280;\">Booking</div>
                                                                            <div style=\"font-size:16px;line-height:20px;color:#111827;font-weight:800;\">#{{BOOKING_ID}}</div>
                                                                        </td>
                                                                    </tr>
                                                                </table>

                                                                <div style=\"height:1px;background:#e5e7eb;margin:12px 0 16px 0;\"></div>

                                                                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"font-family:Arial,Helvetica,sans-serif;border-collapse:collapse;\">
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;\">Khách hàng</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;font-weight:700;\">{{CUSTOMER_NAME}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Email</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{CUSTOMER_EMAIL}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Chi nhánh</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{BRANCH}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Slot</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{SLOT}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Biển số</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{PLATE}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Check-in</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{CHECKIN}}</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Check-out</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">{{CHECKOUT}}</td>
                                                                    </tr>
                                                                </table>

                                                                <div style=\"height:1px;background:#e5e7eb;margin:16px 0;\"></div>

                                                                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"font-family:Arial,Helvetica,sans-serif;border-collapse:collapse;\">
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;\">Tạm tính</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;font-weight:700;\">{{SUBTOTAL}} VND</td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td style=\"padding:10px 0;color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;\">Giảm giá</td>
                                                                        <td style=\"padding:10px 0;color:#111827;font-size:13px;text-align:right;border-top:1px solid #f3f4f6;\">-{{DISCOUNT}} VND</td>
                                                                    </tr>
                                                                    {{VOUCHER_ROW}}
                                                                    <tr>
                                                                        <td style=\"padding:14px 0;color:#111827;font-size:14px;border-top:1px solid #e5e7eb;font-weight:800;\">Tổng thanh toán</td>
                                                                        <td style=\"padding:14px 0;color:#16a34a;font-size:18px;text-align:right;border-top:1px solid #e5e7eb;font-weight:900;\">{{TOTAL}} VND</td>
                                                                    </tr>
                                                                </table>

                                                                <div style=\"margin-top:14px;padding:12px 14px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;\">
                                                                    <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:16px;color:#6b7280;\">
                                                                        Cảm ơn bạn đã sử dụng Smart Parking. Đây là biên lai điện tử được gửi tự động.
                                                                    </div>
                                                                </div>
                                                            </td>
                                                        </tr>

                                                        <tr>
                                                            <td style=\"padding:14px 20px;background:#f9fafb;border-top:1px solid #e5e7eb;\">
                                                                <div style=\"font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:15px;color:#6b7280;\">
                                                                    Smart Parking • Invoice #{{INVOICE_NUMBER}}
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </body>
                                </html>
                                """;

        return template
                .replace("{{INVOICE_NUMBER}}", escapeHtml(invoiceNumber))
                .replace("{{BOOKING_ID}}", escapeHtml(bookingIdText))
                .replace("{{CUSTOMER_NAME}}", escapeHtml(customerLine))
                .replace("{{CUSTOMER_EMAIL}}", escapeHtml(customerEmail))
                .replace("{{BRANCH}}", escapeHtml(branch))
                .replace("{{SLOT}}", escapeHtml(slot))
                .replace("{{PLATE}}", escapeHtml(plate))
                .replace("{{CHECKIN}}", escapeHtml(checkIn))
                .replace("{{CHECKOUT}}", escapeHtml(checkOut))
                .replace("{{SUBTOTAL}}", escapeHtml(subtotal))
                .replace("{{DISCOUNT}}", escapeHtml(discount))
                .replace("{{VOUCHER_ROW}}", voucherRow)
                .replace("{{TOTAL}}", escapeHtml(total));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
