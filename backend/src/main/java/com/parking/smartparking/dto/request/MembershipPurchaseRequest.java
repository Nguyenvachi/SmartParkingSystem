package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MembershipPurchaseRequest {

    @NotBlank(message = "Loại vé tháng không được để trống")
    @Pattern(regexp = "^(MONTHLY)$", message = "Hiện tại chỉ hỗ trợ gói MONTHLY")
    private String plan;

    private Boolean autoRenewMembership = false;
}
