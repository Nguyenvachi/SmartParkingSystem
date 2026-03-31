package com.parking.smartparking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(
        name = "invoices",
        indexes = {
                @Index(name = "idx_invoice_booking_id", columnList = "booking_id", unique = true),
                @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true)
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 64, unique = true)
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    // Snapshot fields (avoid losing info if booking/user changes later)
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_full_name")
    private String userFullName;

    @Column(name = "slot_name")
    private String slotName;

    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "subtotal_amount", precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "voucher_code")
    private String voucherCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "email_sent_to")
    private String emailSentTo;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Lob
    @Column(name = "email_send_error")
    private String emailSendError;
}
