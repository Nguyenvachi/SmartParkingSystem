package com.parking.smartparking.service;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class OcrSimulationServiceTests {

    private OcrSimulationService ocrSimulationService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        ocrSimulationService = new OcrSimulationService();
        ReflectionTestUtils.setField(ocrSimulationService, "defaultConfidence", 0.91d);
    }

    @Test
    void shouldDetectPlateFromFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "29A12345-test.jpg",
                "image/jpeg",
                "fake".getBytes(StandardCharsets.UTF_8));

        var response = ocrSimulationService.simulateRecognition(file);

        assertEquals("29A1-2345", response.getDetectedPlate());
        assertTrue(response.getConfidence() > 0.9d);
    }

    @Test
    void shouldFallbackWhenFileNameHasNoPlatePattern() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "parking-photo.png",
                "image/png",
                "fake".getBytes(StandardCharsets.UTF_8));

        var response = ocrSimulationService.simulateRecognition(file);

        assertTrue(response.getDetectedPlate().contains("-"));
        assertEquals("parking-photo.png", response.getFileName());
        assertTrue(response.getConfidence() < 0.91d);
    }

    @Test
    void shouldGenerateSafeFallbackPlateForVeryShortFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "x.jpg",
                "image/jpeg",
                "fake".getBytes(StandardCharsets.UTF_8));

        var response = ocrSimulationService.simulateRecognition(file);

        assertTrue(response.getDetectedPlate().matches("[0-9]{2}[A-Z]{2}-[0-9]{5}"));
        assertTrue(response.getConfidence() >= 0.5d);
    }
}
