package com.parking.smartparking.service;

import java.util.Optional;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.entity.AuthProvider;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * CustomOAuth2UserService - Xử lý logic lưu User từ Google vào Database
 *
 * Tech Key #10: Middleware/Filter - Tích hợp OAuth2
 *
 * Chức năng: 1. Nhận thông tin từ Google (email, name, picture) 2. Kiểm tra
 * user đã tồn tại trong DB chưa 3. Nếu có: Cập nhật thông tin (avatar,
 * fullName) 4. Nếu chưa có: Tạo mới user với authProvider = GOOGLE
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // Lấy thông tin từ Google trả về
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatar = oAuth2User.getAttribute("picture");

        // Kiểm tra xem user đã tồn tại chưa
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            // Nếu có rồi -> Cập nhật thông tin
            User existingUser = userOptional.get();
            existingUser.setFullName(name);
            existingUser.setAvatarUrl(avatar);
            existingUser.setAuthProvider(AuthProvider.GOOGLE);
            existingUser.setIsEmailVerified(true);
            userRepository.save(existingUser);

            System.out.println("✅ Updated existing user from Google: " + email);
        } else {
            // Nếu chưa có -> Tạo mới
            User newUser = User.builder()
                    .email(email)
                    .fullName(name)
                    .avatarUrl(avatar)
                    .password("") // Không có password với Google login
                    .role(User.Role.ROLE_USER)
                    .authProvider(AuthProvider.GOOGLE)
                    .isEmailVerified(true) // Google đã xác thực rồi
                    .build();
            userRepository.save(newUser);

            System.out.println("✅ Created new user from Google: " + email);
        }

        return oAuth2User;
    }
}
