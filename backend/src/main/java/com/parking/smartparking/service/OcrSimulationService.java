package com.parking.smartparking.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.parking.smartparking.dto.response.OcrSimulationResponse;

@Service
public class OcrSimulationService {

    private static final Pattern PLATE_PATTERN = Pattern.compile("([0-9]{2}[A-Z]{1,2}[0-9]{4,6})");
    private static final String FALLBACK_SEED = "29A12345";

    @Value("${app.ocr.default-confidence:0.91}")
    private double defaultConfidence;

    public OcrSimulationResponse simulateRecognition(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ảnh xe để mô phỏng OCR.");
        }

        String originalFilename = file.getOriginalFilename();
        String fileName = (originalFilename == null || originalFilename.isBlank()) ? "unknown.jpg" : originalFilename;
        String normalized = fileName
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\.[A-Z0-9]+$", "")
                .replaceAll("[^A-Z0-9]", "");

        Matcher matcher = PLATE_PATTERN.matcher(normalized);
        boolean matched = matcher.find();
        String detectedPlate = matched ? matcher.group(1) : buildFallbackPlate(normalized);
        double confidence = matched ? defaultConfidence : Math.max(defaultConfidence - 0.16d, 0.5d);

        return OcrSimulationResponse.builder()
                .fileName(fileName)
                .detectedPlate(formatPlate(detectedPlate))
                .confidence(confidence)
                .message("OCR Simulation thành công. Hệ thống mock nhận diện biển số từ tên file upload.")
                .build();
    }

    private String buildFallbackPlate(String normalized) {
        String seed = padRight((normalized + FALLBACK_SEED).replaceAll("[^A-Z0-9]", ""), 9, '1');
        String head = seed.substring(0, 2).replaceAll("[^0-9]", "2");
        String middle = seed.substring(2, 4).replaceAll("[^A-Z]", "A");
        String tail = seed.substring(4, 9).replaceAll("[^0-9]", "1");
        return head + middle + tail;
    }

    private String padRight(String value, int targetLength, char padChar) {
        if (value.length() >= targetLength) {
            return value;
        }

        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < targetLength) {
            builder.append(padChar);
        }
        return builder.toString();
    }

    private String formatPlate(String plate) {
        if (plate.length() < 7) {
            return plate;
        }
        return plate.substring(0, 4) + "-" + plate.substring(4);
    }
}
