package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.AdminUserStatusRequest;
import com.parking.smartparking.dto.request.AdminUserUpdateRequest;
import com.parking.smartparking.dto.response.AdminDashboardSummaryResponse;
import com.parking.smartparking.dto.response.AdminUserResponse;
import com.parking.smartparking.dto.response.PagedResponse;
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

    @GetMapping("/users/search")
    public ResponseEntity<PagedResponse<AdminUserResponse>> searchUsers(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            Authentication authentication) {

        return ResponseEntity.ok(
                adminManagementService.searchManagedUsers(authentication.getName(), keyword, page, size, sortBy, sortDir));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUserAccess(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(adminManagementService.updateUserAccess(id, request, authentication.getName()));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<AdminUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                adminManagementService.updateUserStatus(id, Boolean.TRUE.equals(request.getActive()), authentication.getName()));
    }
}
