package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String phoneNumber;

    @Size(max = 2048, message = "Avatar URL quá dài")
    private String avatarUrl;
}
