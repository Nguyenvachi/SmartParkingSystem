package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.UserVehicleCreateRequest;
import com.parking.smartparking.dto.request.UserVehicleUpdateRequest;
import com.parking.smartparking.dto.response.MessageResponse;
import com.parking.smartparking.dto.response.UserVehicleResponse;
import com.parking.smartparking.service.UserVehicleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class UserVehicleController {

    private final UserVehicleService userVehicleService;

    @GetMapping
    public ResponseEntity<List<UserVehicleResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(userVehicleService.listMyVehicles(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<UserVehicleResponse> create(
            @Valid @RequestBody UserVehicleCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userVehicleService.createVehicle(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserVehicleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserVehicleUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(userVehicleService.updateVehicle(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(
            @PathVariable Long id,
            Authentication authentication) {
        userVehicleService.deleteVehicle(authentication.getName(), id);
        return ResponseEntity.ok(MessageResponse.builder().message("Đã xóa phương tiện.").build());
    }
}
