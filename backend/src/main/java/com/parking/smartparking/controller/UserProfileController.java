package com.parking.smartparking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.ChangePasswordRequest;
import com.parking.smartparking.dto.request.UpdateUserProfileRequest;
import com.parking.smartparking.dto.request.UpdateUserSettingsRequest;
import com.parking.smartparking.dto.response.MessageResponse;
import com.parking.smartparking.dto.response.UserProfileResponse;
import com.parking.smartparking.service.UserProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMe(Authentication authentication) {
        return ResponseEntity.ok(userProfileService.getMe(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateMe(authentication.getName(), request));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserProfileResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ResponseEntity.ok(userProfileService.updateSettings(authentication.getName(), request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(new MessageResponse("Đổi mật khẩu thành công."));
    }
}
