package com.parking.smartparking.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingRecommendationResponse {

    private String requestedType;
    private ParkingSlotResponse recommendedSlot;
    private List<ParkingSlotResponse> alternativeSlots;
    private String explanation;
}
