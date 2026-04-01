package com.parking.smartparking.config;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.service.JwtService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unused"})
class JwtAuthFilterTests {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseCurrentRoleFromDatabaseInsteadOfStaleTokenRole() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(User.builder()
                .email("admin@test.com")
                .role(User.Role.ROLE_BRANCH_ADMIN)
                .branchCode("HCM")
                .build()));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("admin@test.com", authentication.getName());
        assertEquals("ROLE_BRANCH_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldLeaveSecurityContextEmptyWhenUserNoLongerExists() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("deleted@test.com");
        when(userRepository.findByEmail("deleted@test.com")).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectDisabledUserToken() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/summary");
        request.addHeader("Authorization", "Bearer valid-token");

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("disabled@test.com");
        when(userRepository.findByEmail("disabled@test.com")).thenReturn(Optional.of(User.builder()
                .email("disabled@test.com")
                .role(User.Role.ROLE_USER)
                .isActive(false)
                .build()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
