package com.parking.smartparking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private Boolean emailVerified;
    private String authProvider;

    private Boolean notificationEmailEnabled;
    private Boolean notificationPushEnabled;
}
