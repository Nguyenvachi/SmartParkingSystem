package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.AdminUserUpdateRequest;
import com.parking.smartparking.dto.response.AdminDashboardSummaryResponse;
import com.parking.smartparking.dto.response.AdminUserResponse;
import com.parking.smartparking.service.AdminManagementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    @GetMapping("/summary")
    public ResponseEntity<AdminDashboardSummaryResponse> getSummary(Authentication authentication) {
        return ResponseEntity.ok(adminManagementService.getSummary(authentication.getName()));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getUsers(Authentication authentication) {
        return ResponseEntity.ok(adminManagementService.getManagedUsers(authentication.getName()));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUserAccess(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(adminManagementService.updateUserAccess(id, request, authentication.getName()));
    }
}
