package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.BlacklistVehicleRequest;
import com.parking.smartparking.dto.response.BlacklistedVehicleResponse;
import com.parking.smartparking.service.BlacklistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistService blacklistService;

    @GetMapping
    public ResponseEntity<List<BlacklistedVehicleResponse>> getVisibleBlacklist(Authentication authentication) {
        return ResponseEntity.ok(blacklistService.getVisibleBlacklist(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<BlacklistedVehicleResponse> createEntry(
            @Valid @RequestBody BlacklistVehicleRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blacklistService.createEntry(request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateEntry(@PathVariable Long id, Authentication authentication) {
        blacklistService.deactivateEntry(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
