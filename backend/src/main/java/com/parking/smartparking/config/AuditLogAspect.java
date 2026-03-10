package com.parking.smartparking.config;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.parking.smartparking.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Around("within(com.parking.smartparking.controller..*) && (execution(@org.springframework.web.bind.annotation.PostMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PutMapping * *(..)) || execution(@org.springframework.web.bind.annotation.DeleteMapping * *(..)))")
    public Object auditControllerMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = resolveRequest();
        String actorEmail = resolveActorEmail();
        String action = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
        String target = joinPoint.getSignature().getDeclaringType().getSimpleName().replace("Controller", "");
        String details = summarizeArguments(joinPoint);

        try {
            Object result = joinPoint.proceed();
            auditLogService.record(actorEmail, resolveMethod(request), resolvePath(request), action, target, "SUCCESS", details);
            return result;
        } catch (Throwable ex) {
            auditLogService.record(actorEmail, resolveMethod(request), resolvePath(request), action, target, "FAILURE",
                    trimDetails(details + " | error=" + ex.getMessage()));
            throw ex;
        }
    }

    private HttpServletRequest resolveRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String resolveActorEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String resolveMethod(HttpServletRequest request) {
        return request != null ? request.getMethod() : "N/A";
    }

    private String resolvePath(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "N/A";
    }

    private String summarizeArguments(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < args.length; index++) {
            if (args[index] == null) {
                continue;
            }
            String parameterName = parameterNames != null && parameterNames.length > index ? parameterNames[index] : "arg" + index;
            builder.append(parameterName).append('=').append(args[index]).append(';');
        }

        return trimDetails(builder.length() > 0 ? builder.toString() : Arrays.toString(args));
    }

    private String trimDetails(String details) {
        if (details == null) {
            return null;
        }
        return details.length() > 500 ? details.substring(0, 500) : details;
    }
}
