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

    @Value("${app.ocr.default-confidence:0.91}")
    private double defaultConfidence;

    public OcrSimulationResponse simulateRecognition(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ảnh xe để mô phỏng OCR.");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.jpg";
        String normalized = fileName
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\.[A-Z0-9]+$", "")
                .replaceAll("[^A-Z0-9]", "");

        Matcher matcher = PLATE_PATTERN.matcher(normalized);
        String detectedPlate = matcher.find() ? matcher.group(1) : buildFallbackPlate(normalized);

        return OcrSimulationResponse.builder()
                .fileName(fileName)
                .detectedPlate(formatPlate(detectedPlate))
                .confidence(defaultConfidence)
                .message("OCR Simulation thành công. Hệ thống mock nhận diện biển số từ tên file upload.")
                .build();
    }

    private String buildFallbackPlate(String normalized) {
        String seed = (normalized + "29A12345");
        String head = seed.substring(0, 2).replaceAll("[^0-9]", "2");
        String middle = seed.substring(2, 4).replaceAll("[^A-Z]", "A");
        String tail = seed.substring(4, 9).replaceAll("[^0-9]", "1");
        return head + middle + tail;
    }

    private String formatPlate(String plate) {
        if (plate.length() < 7) {
            return plate;
        }
        return plate.substring(0, 4) + "-" + plate.substring(4);
    }
}
