package com.parking.smartparking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.parking.smartparking.service.CustomOAuth2UserService;

import lombok.RequiredArgsConstructor;

/**
 * Cấu hình bảo mật cho hệ thống Tech Key: Tính năng #10 - Middleware/Filter
 *
 * Chức năng: 1. Định nghĩa các API nào được truy cập tự do (permitAll) 2. Các
 * API còn lại bắt buộc phải đăng nhập (authenticated) 3. Cung cấp Bean mã hóa
 * mật khẩu (BCrypt - Chuẩn Enterprise) 4. Tích hợp Google OAuth2 Login
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter; // [FIX 2 - JWT] Filter xác thực token mọi request

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.frontend.login-path:/login}")
    private String frontendLoginPath;

    /**
     * Cấu hình bộ lọc bảo mật (Security Filter Chain)
     *
     * Quy tắc: - /api/auth/** : Cho phép truy cập (Đăng ký, Đăng nhập) -
     * /index.html, /login.html, /css/**, /js/** : Cho phép (File tĩnh Frontend)
     * - Các API còn lại: Bắt buộc đăng nhập
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // [FIX] Dùng Customizer.withDefaults() thay vì cors.configure(http)
                // Cách cũ: cors -> cors.configure(http) gọi configure() 2 lần -> bút CorsFilter 2 lần
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Payment gateway callbacks (no JWT)
                .requestMatchers("/api/payments/callback/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/audit-logs/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/summary").hasAnyAuthority("ROLE_ADMIN", "ROLE_BRANCH_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/users/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/admin/users/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/blacklist/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_BRANCH_ADMIN")
                // [FIX 4] Phân quyền theo HTTP Method cho Slot API
                .requestMatchers(HttpMethod.GET, "/api/slots/**").permitAll() // Xem: Tự do
                .requestMatchers(HttpMethod.POST, "/api/slots/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_BRANCH_ADMIN") // Tạo: Admin
                .requestMatchers(HttpMethod.PUT, "/api/slots/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_BRANCH_ADMIN") // Sửa: Admin
                .requestMatchers(HttpMethod.DELETE, "/api/slots/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_BRANCH_ADMIN") // Xóa: Admin
                // Cho phép truy cập tự do
                .requestMatchers(
                        "/api/auth/**",
                        "/ws/**",
                        "/login/oauth2/**",
                        "/oauth2/**",
                        "/error",
                        "/",
                        "/index.html",
                        "/login",
                        "/login.html",
                        "/register",
                        "/register.html",
                        "/dashboard",
                        "/dashboard.html",
                        "/css/**",
                        "/js/**",
                        "/assets/**",
                        "/images/**"
                ).permitAll()
                // Tất cả request khác bắt buộc đăng nhập
                .anyRequest().authenticated()
                )
                // --- GOOGLE OAUTH2 LOGIN ---
                .oauth2Login(oauth2 -> oauth2
                .loginPage(frontendBaseUrl + frontendLoginPath) // Trang login ở Frontend
                .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService) // Dùng service tự viết để lưu DB
                )
                .successHandler(oAuth2SuccessHandler) // Xử lý sau khi login thành công
                .failureUrl(frontendBaseUrl + frontendLoginPath + "?error=oauth2") // Xử lý khi login thất bại
                )
                // [FIX 2 - JWT] Xác thực JWT Token trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // [FIX 5] Trả về 401 JSON thay vì redirect khi không có token (tránh CORS redirect loop)
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint(apiAuthEntryPoint())
                .accessDeniedHandler(apiAccessDeniedHandler()));

        return http.build();
    }

    /**
     * AuthenticationEntryPoint: khi request vào /api/** không có JWT Thay vì
     * redirect sang /login (gây CORS), trả về 401 JSON.
     */
    @Bean
    public AuthenticationEntryPoint apiAuthEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"message\":\"Bạn chưa đăng nhập hoặc token hết hạn. Vui lòng đăng nhập lại.\",\"status\":401}");
        };
    }

    @Bean
    public AccessDeniedHandler apiAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"message\":\"Bạn không có quyền thực hiện thao tác này.\",\"status\":403}");
        };
    }

    /**
     * Bean mã hóa mật khẩu
     *
     * BCrypt: Thuật toán mã hóa 1 chiều (không thể giải mã ngược) Cơ chế:
     * password + salt (chuỗi ngẫu nhiên) -> Hash
     *
     * Ví dụ: Input: "123456" Output:
     * "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Bean quản lý xác thực Dùng để verify username/password khi đăng nhập
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
