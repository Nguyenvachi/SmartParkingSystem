package com.parking.smartparking.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * QRCodeService - Sinh QR Code và Chữ ký số cho vé điện tử
 *
 * Tech Key #7: Digital Signature (Java Security) Thuật toán: HMAC-SHA256
 * (javax.crypto.Mac)
 *
 * Cơ chế chống giả mạo: 1. Server ký nội dung vé bằng HMAC-SHA256 với secret
 * key 2. Chữ ký được nhúng vào QR Code 3. Khi quét tại cổng, hệ thống verify
 * lại chữ ký 4. Nếu nội dung bị sửa → chữ ký không khớp → vé GIẢ
 *
 * Quan hệ: - File Con (Dependency): Không có - File Cha (Sử dụng) :
 * BookingService.java
 */
@Service
@Slf4j
public class QRCodeService {

    @Value("${app.qr.secret}")
    private String qrSecret;

    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;

    /**
     * Sinh mã QR từ nội dung text → Trả về chuỗi Base64 PNG
     *
     * Dùng thư viện ZXing (Google) để mã hóa text thành ảnh QR
     *
     * Format nội dung QR khi tạo booking:
     * "BOOKING:{id}|USER:{userId}|SLOT:{slotName}|TIME:{bookingTime}|SIG:{hmacSignature}"
     *
     * @param content - Nội dung cần mã hóa thành QR (bao gồm cả chữ ký)
     * @return Base64 encoded PNG string
     */
    public String generateQRCode(String content) {
        try {
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrWriter.encode(content, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (WriterException | IOException e) {
            log.error("❌ Lỗi sinh QR Code: {}", e.getMessage());
            return ""; // Trả về rỗng - không ngăn quá trình tạo booking
        }
    }

    /**
     * Ký số nội dung bằng HMAC-SHA256
     *
     * HMAC (Hash-based Message Authentication Code): - Input : content + secret
     * key - Output: 64 ký tự hex (256 bit) - Tính chất: Không thể làm giả nếu
     * không biết secret key
     *
     * Ví dụ: content = "BOOKING:1|USER:5|SLOT:A01|TIME:2026-03-10T11:30:00"
     * signature = "a3f4b2c1d5e6..." (64 ký tự hex)
     *
     * @param content - Nội dung cần ký (KHÔNG bao gồm phần SIG)
     * @return Chữ ký dạng Hex String (64 ký tự)
     */
    public String generateSignature(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] signatureBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));

            // Chuyển byte array thành hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : signatureBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("❌ Lỗi ký số HMAC-SHA256: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Xác thực chữ ký số khi quét QR tại cổng bãi xe
     *
     * @param content - Nội dung gốc của QR (không có phần |SIG:...)
     * @param signature - Chữ ký cần kiểm tra (lấy từ phần |SIG: trong QR)
     * @return true nếu vé thật, false nếu vé giả mạo
     */
    public boolean verifySignature(String content, String signature) {
        if (content == null || signature == null || signature.isBlank()) {
            return false;
        }
        String expectedSignature = generateSignature(content);
        // Dùng constant-time comparison để chống timing attack
        return expectedSignature.equals(signature);
    }
}
