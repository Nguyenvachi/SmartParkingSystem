package com.parking.smartparking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrSimulationResponse {

    private String fileName;
    private String detectedPlate;
    private double confidence;
    private String message;
}
