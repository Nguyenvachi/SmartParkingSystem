package com.parking.smartparking.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.parking.smartparking.dto.response.OcrSimulationResponse;
import com.parking.smartparking.service.OcrSimulationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrSimulationService ocrSimulationService;

    @PostMapping(value = "/simulate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrSimulationResponse> simulate(@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(ocrSimulationService.simulateRecognition(image));
    }
}
