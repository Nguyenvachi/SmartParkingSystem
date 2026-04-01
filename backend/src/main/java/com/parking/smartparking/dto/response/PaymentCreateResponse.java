package com.parking.smartparking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCreateResponse {

    private String provider;
    private String orderId;
    private String paymentUrl;
}
