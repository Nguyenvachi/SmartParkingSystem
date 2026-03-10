package com.parking.smartparking.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.parking.smartparking.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JwtAuthFilter - Filter xác thực JWT Token trên MỖI HTTP request Tech Key #10:
 * Middleware/Filter (Authentication Stateless)
 *
 * Flow: 1. Nhận HTTP request 2. Đọc header "Authorization: Bearer <token>" 3.
 * Validate token bằng JwtService (chữ ký + hết hạn) 4. Nếu hợp lệ: Set
 * Authentication vào SecurityContextHolder 5. Tiếp tục chain -> Controller xử
 * lý
 *
 * Quan hệ File: - File Cha (Đăng ký filter): SecurityConfig.java
 * (.addFilterBefore) - File Con (Dependency) : JwtService.java (xác thực +
 * decode token)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Bỏ qua nếu không có Authorization header hoặc không phải Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Cắt "Bearer " (7 ký tự) để lấy token thuần
        String token = authHeader.substring(7);

        // [FIX] Luôn validate JWT nếu có Bearer header, KHÔNG kiểm tra context cũ.
        // Lý do: OAuth2 session có thể đã set context trước, khiến JWT bị bỏ qua.
        if (jwtService.isTokenValid(token)) {
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);

            log.debug("✅ JWT hợp lệ | email={} | role={}", email, role);

            // Tạo Authentication object với role lấy từ JWT
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    email,
                    null, // credentials = null (đã xác thực qua chữ ký token)
                    List.of(new SimpleGrantedAuthority(role)) // GrantedAuthority từ JWT
            );

            // Ghi vào SecurityContextHolder (ghi đè mọi session-based auth cũ)
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } else {
            log.warn("❌ JWT không hợp lệ hoặc hết hạn | URI={} | token_prefix={}",
                    request.getRequestURI(),
                    token.length() > 20 ? token.substring(0, 20) + "..." : token);
        }

        filterChain.doFilter(request, response);
    }
}
