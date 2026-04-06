package com.parking.smartparking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserSettingsRequest {

    @NotNull
    private Boolean notificationEmailEnabled;

    @NotNull
    private Boolean notificationPushEnabled;
}
