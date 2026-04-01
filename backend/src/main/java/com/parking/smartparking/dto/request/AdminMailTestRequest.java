package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminMailTestRequest {

    @NotBlank(message = "Email người nhận không được để trống")
    @Email(message = "Email người nhận không hợp lệ")
    private String to;

    @NotBlank(message = "Tiêu đề email không được để trống")
    private String subject;

    @NotBlank(message = "Nội dung email không được để trống")
    private String body;
}
