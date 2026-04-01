package com.parking.smartparking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordResponse {

    private String message;

    /**
     * Chỉ dùng cho demo/dev khi bật cấu hình expose token.
     */
    private String resetToken;
}
