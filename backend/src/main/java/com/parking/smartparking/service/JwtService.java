package com.parking.smartparking.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtService - Tạo và xác thực JWT Token (JSON Web Token) Tech Key #10:
 * Middleware/Filter - Authentication Stateless
 *
 * JWT Structure: Header.Payload.Signature - Header : Thuật toán mã hóa (HS256)
 * - Payload : userId, email, role, exp (expiration time) - Signature:
 * HMAC(Header.Payload, secretKey) chống giả mạo
 *
 * Quan hệ File: - File Con (Dependency): Không có dependency nội bộ - File Cha
 * (Sử dụng) : AuthService.java (tạo token khi login/register)
 * JwtAuthFilter.java (validate token mỗi request)
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpiration; // Mặc định: 86400000ms = 24 giờ

    /**
     * Tạo SecretKey từ chuỗi secret trong application.properties (HMAC-SHA256)
     * Yêu cầu: key phải >= 256 bit (32 ký tự ASCII)
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Tạo JWT Token sau khi đăng nhập / đăng ký thành công
     *
     * @param email - Subject của token (định danh user)
     * @param role - Quyền hạn (ROLE_USER / ROLE_ADMIN)
     * @param userId - ID người dùng trong DB
     * @return JWT Token dạng String (Header.Payload.Signature)
     */
    public String generateToken(String email, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Lấy email (subject) từ token đã decode
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Lấy role từ token đã decode
     */
    public String extractRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    /**
     * Lấy userId từ token đã decode Lưu ý: JSON số nguyên nhỏ có thể bị parse
     * thành Integer thay vì Long
     */
    public Long extractUserId(String token) {
        Object userId = parseClaims(token).get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * Kiểm tra token có hợp lệ không (chữ ký đúng VÀ chưa hết hạn)
     *
     * @param token - JWT Token cần kiểm tra
     * @return true nếu hợp lệ, false nếu không hợp lệ hoặc hết hạn
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Helper: Parse token và trả về Claims (không ném exception ra ngoài)
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
