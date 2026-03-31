package com.parking.smartparking.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.parking.smartparking.dto.response.ForgotPasswordResponse;
import com.parking.smartparking.entity.PasswordResetToken;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.PasswordResetTokenRepository;
import com.parking.smartparking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PasswordResetServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Test
    void shouldCreateResetTokenAndExposeWhenEnabled() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                encoder,
                mailSenderProvider);
        ReflectionTestUtils.setField(service, "expiryMinutes", 15);
        ReflectionTestUtils.setField(service, "exposeToken", true);

        User user = User.builder().id(10L).email("user@test.com").fullName("User").password("old").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ForgotPasswordResponse response = service.createResetToken("user@test.com");
        assertNotNull(response.getMessage());
        assertNotNull(response.getResetToken());
        assertTrue(response.getResetToken().length() >= 20);
    }

    @Test
    void shouldResetPasswordWithValidToken() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                encoder,
                mailSenderProvider);
        ReflectionTestUtils.setField(service, "expiryMinutes", 15);
        ReflectionTestUtils.setField(service, "exposeToken", false);

        User user = User.builder().id(10L).email("user@test.com").fullName("User").password("old").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = "demo-token";
        PasswordResetToken tokenEntity = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(encoder.encode(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(passwordResetTokenRepository
                .findTopByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(10L), any(LocalDateTime.class)))
                .thenReturn(Optional.of(tokenEntity));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resetPassword("user@test.com", rawToken, "newpass123", "newpass123");

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(tokenEntity);
        assertNotNull(tokenEntity.getUsedAt());
    }

    @Test
    void shouldRejectResetWhenTokenDoesNotMatch() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                encoder,
                mailSenderProvider);
        ReflectionTestUtils.setField(service, "expiryMinutes", 15);

        User user = User.builder().id(10L).email("user@test.com").fullName("User").password("old").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        PasswordResetToken tokenEntity = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(encoder.encode("right-token"))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(passwordResetTokenRepository
                .findTopByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(10L), any(LocalDateTime.class)))
                .thenReturn(Optional.of(tokenEntity));

        assertThrows(RuntimeException.class,
                () -> service.resetPassword("user@test.com", "wrong-token", "newpass123", "newpass123"));
    }
}
