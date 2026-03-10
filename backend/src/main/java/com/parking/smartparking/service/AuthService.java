package com.parking.smartparking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.AuthRequest;
import com.parking.smartparking.dto.request.RegisterRequest;
import com.parking.smartparking.dto.response.AuthResponse;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service xử lý logic nghiệp vụ cho Authentication
 *
 * Quy tắc Enterprise: Controller KHÔNG được chứa logic nghiệp vụ - Controller:
 * Nhận request, gọi Service, trả response - Service: Xử lý logic (validate,
 * transform, gọi Repository) - Repository: Giao tiếp với Database
 *
 * Tech Key: - @Transactional: Đảm bảo tính toàn vẹn dữ liệu (ACID) -
 * PasswordEncoder: Mã hóa mật khẩu bằng BCrypt
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // [FIX 2 - JWT] Sinh token sau khi auth thành công

    /**
     * Đăng ký tài khoản mới
     *
     * Luồng xử lý: 1. Kiểm tra email đã tồn tại chưa 2. Mã hóa mật khẩu bằng
     * BCrypt 3. Lưu user vào Database 4. Trả về thông tin user (KHÔNG trả về
     * password)
     *
     * @param request - Dữ liệu đăng ký từ Frontend
     * @return AuthResponse - Thông tin user sau khi đăng ký
     * @throws RuntimeException - Nếu email đã tồn tại
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Bước 1: Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng. Vui lòng chọn email khác.");
        }

        // Bước 2: Tạo user mới (Builder Pattern)
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Mã hóa password
                // [FIX 1 - SECURITY] Luôn gán ROLE_USER, không tin request.getRole()
                // .role(request.getRole())  // [DISABLED - Kẻ tấn công không được tự set ROLE_ADMIN]
                .role(User.Role.ROLE_USER) // Server quyết định role, không phải client
                .build();

        // Bước 3: Lưu vào Database
        User savedUser = userRepository.save(user);

        // Bước 4: Trả về response (KHÔNG có password)
        return AuthResponse.builder()
                .userId(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .branchCode(savedUser.getBranchCode())
                // [FIX 2 - JWT] Sinh token ngay sau đăng ký để Frontend dùng luôn
                .token(jwtService.generateToken(savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId()))
                .message("Đăng ký tài khoản thành công!")
                .build();
    }

    /**
     * Đăng nhập
     *
     * Luồng xử lý: 1. Tìm user theo email 2. So sánh mật khẩu (dùng
     * passwordEncoder.matches) 3. Nếu đúng -> Trả về thông tin user 4. Nếu sai
     * -> Throw exception
     *
     * @param request - Email và mật khẩu từ Frontend
     * @return AuthResponse - Thông tin user
     * @throws RuntimeException - Nếu email/password sai
     */
    public AuthResponse login(AuthRequest request) {
        // Bước 1: Tìm user theo email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không đúng"));

        // Bước 2: Kiểm tra mật khẩu
        // passwordEncoder.matches() sẽ:
        // - Lấy password từ request (plain text): "123456"
        // - Lấy password từ DB (đã hash): "$2a$10$N9qo8..."
        // - Hash password từ request và so sánh
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng");
        }

        // Bước 3: Đăng nhập thành công, trả về thông tin
        return AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .branchCode(user.getBranchCode())
                // [FIX 2 - JWT] Sinh token để Frontend lưu và gửi trong Authorization header
                .token(jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId()))
                .message("Đăng nhập thành công!")
                .build();
    }
}
