package com.parking.smartparking.service;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QRCodeServiceTests {

    private QRCodeService qrCodeService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        qrCodeService = new QRCodeService();
        ReflectionTestUtils.setField(qrCodeService, "qrSecret", "test-only-qr-signing-key-32-characters");
    }

    @Test
    void shouldVerifyValidSignatureAndRejectTamperedPayload() {
        String payload = qrCodeService.buildBookingPayload(1L, 2L, "A01", LocalDateTime.of(2026, 3, 10, 10, 15), "29A-12345");
        String signature = qrCodeService.generateSignature(payload);

        assertTrue(qrCodeService.verifySignature(payload, signature));
        assertFalse(qrCodeService.verifySignature(payload + "-tampered", signature));
    }

    @Test
    void shouldNormalizeVehiclePlateInsidePayload() {
        String payload = qrCodeService.buildBookingPayload(2L, 5L, "B02", LocalDateTime.of(2026, 3, 10, 11, 45), "51f-99999");

        assertTrue(payload.contains("PLATE:51F99999"));
    }
}
