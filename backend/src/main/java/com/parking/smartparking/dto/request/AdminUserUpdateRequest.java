package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @NotBlank(message = "Role không được để trống")
    @Pattern(regexp = "^(ROLE_USER|ROLE_BRANCH_ADMIN|ROLE_ADMIN)$", message = "Role không hợp lệ")
    private String role;

    @Pattern(regexp = "^$|^[A-Z0-9_-]{2,20}$", message = "Mã chi nhánh chỉ gồm chữ in hoa, số, _ hoặc -")
    private String branchCode;
}
