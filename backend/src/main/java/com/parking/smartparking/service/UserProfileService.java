package com.parking.smartparking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.ChangePasswordRequest;
import com.parking.smartparking.dto.request.UpdateUserProfileRequest;
import com.parking.smartparking.dto.request.UpdateUserSettingsRequest;
import com.parking.smartparking.dto.response.UserProfileResponse;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateMe(String email, UpdateUserProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        user.setFullName(request.getFullName().trim());
        user.setPhoneNumber(safeTrim(request.getPhoneNumber()));
        user.setAvatarUrl(safeTrim(request.getAvatarUrl()));

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserProfileResponse updateSettings(String email, UpdateUserSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        user.setNotificationEmailEnabled(Boolean.TRUE.equals(request.getNotificationEmailEnabled()));
        user.setNotificationPushEnabled(Boolean.TRUE.equals(request.getNotificationPushEnabled()));

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Xác nhận mật khẩu không khớp.");
        }

        final String existingHash = user.getPassword();
        final boolean hasLocalPassword = existingHash != null && !existingHash.isBlank();
        if (hasLocalPassword) {
            final String current = request.getCurrentPassword();
            if (current == null || current.isBlank()) {
                throw new RuntimeException("Vui lòng nhập mật khẩu hiện tại.");
            }
            if (!passwordEncoder.matches(current, existingHash)) {
                throw new RuntimeException("Mật khẩu hiện tại không đúng.");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null)
                .notificationEmailEnabled(Boolean.TRUE.equals(user.getNotificationEmailEnabled()))
                .notificationPushEnabled(Boolean.TRUE.equals(user.getNotificationPushEnabled()))
                .build();
    }

    private String safeTrim(String raw) {
        if (raw == null) {
            return null;
        }
        final String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
