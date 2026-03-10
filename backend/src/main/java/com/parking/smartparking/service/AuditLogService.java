package com.parking.smartparking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.response.AuditLogResponse;
import com.parking.smartparking.entity.AuditLog;
import com.parking.smartparking.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Value("${app.audit.recent-limit:100}")
    private int recentLimit;

    @Transactional
    public void record(String actorEmail, String httpMethod, String requestPath, String action, String target, String result,
            String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .actorEmail(actorEmail != null ? actorEmail : "anonymous")
                    .httpMethod(httpMethod)
                    .requestPath(requestPath)
                    .action(action)
                    .target(target)
                    .result(result)
                    .details(details)
                    .build());
        } catch (Exception ex) {
            log.warn("Không thể ghi audit log cho action {}: {}", action, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(recentLimit)
                .map(log -> AuditLogResponse.builder()
                .id(log.getId())
                .actorEmail(log.getActorEmail())
                .httpMethod(log.getHttpMethod())
                .requestPath(log.getRequestPath())
                .action(log.getAction())
                .target(log.getTarget())
                .result(log.getResult())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build())
                .toList();
    }
}
