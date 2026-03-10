package com.parking.smartparking.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.parking.smartparking.service.AuditLogService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuditLogAspectTests {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @AfterEach
        @SuppressWarnings("unused")
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRedactSensitiveArgumentsWhenRecordingAuditLog() throws Throwable {
        AuditLogAspect auditLogAspect = new AuditLogAspect(auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test.com", null));

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(DummyController.class);
        when(methodSignature.getName()).thenReturn("login");
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"password", "token", "note"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"plain-secret", "jwt-token", "safe-note"});
        when(joinPoint.proceed()).thenReturn("ok");

        auditLogAspect.auditControllerMutation(joinPoint);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(
                eq("admin@test.com"),
                eq("POST"),
                eq("/api/auth/login"),
                eq("DummyController.login"),
                eq("Dummy"),
                eq("SUCCESS"),
                detailsCaptor.capture());

        String details = detailsCaptor.getValue();
        assertTrue(details.contains("[REDACTED]"));
        assertTrue(!details.contains("plain-secret"));
        assertTrue(!details.contains("jwt-token"));
        assertTrue(details.contains("safe-note"));
    }

    @Test
    void shouldRecordFailureWhenControllerThrows() throws Throwable {
        AuditLogAspect auditLogAspect = new AuditLogAspect(auditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/slots/1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test.com", null));

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(DummyController.class);
        when(methodSignature.getName()).thenReturn("delete");
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
        when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

        RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> auditLogAspect.auditControllerMutation(joinPoint));

        assertEquals("boom", exception.getMessage());
        verify(auditLogService).record(
                eq("admin@test.com"),
                eq("DELETE"),
                eq("/api/slots/1"),
                eq("DummyController.delete"),
                eq("Dummy"),
                eq("FAILURE"),
                org.mockito.ArgumentMatchers.contains("error=boom"));
    }

    static class DummyController {
    }
}
