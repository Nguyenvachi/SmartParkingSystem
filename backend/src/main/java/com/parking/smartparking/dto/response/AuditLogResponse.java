package com.parking.smartparking.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String actorEmail;
    private String httpMethod;
    private String requestPath;
    private String action;
    private String target;
    private String result;
    private String details;
    private LocalDateTime createdAt;
}
