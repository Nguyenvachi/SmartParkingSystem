package com.parking.smartparking.service;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MembershipSchedulerServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Test
    void shouldProcessAllEligibleUsersForAutoRenew() {
        MembershipSchedulerService membershipSchedulerService = new MembershipSchedulerService(userRepository, walletService);
        ReflectionTestUtils.setField(membershipSchedulerService, "autoRenewIntervalMs", 300000L);

        User firstUser = createEligibleUser(1L, "renew1@test.com");
        User secondUser = createEligibleUser(2L, "renew2@test.com");

        when(userRepository.findByMembershipPlanAndAutoRenewMembershipTrueAndMembershipExpiryBefore(
                any(),
                any())).thenReturn(List.of(firstUser, secondUser));
        when(walletService.processAutoRenewMembership(firstUser)).thenReturn(true);
        when(walletService.processAutoRenewMembership(secondUser)).thenReturn(false);

        membershipSchedulerService.autoRenewMemberships();

        verify(walletService).processAutoRenewMembership(firstUser);
        verify(walletService).processAutoRenewMembership(secondUser);
    }

    @Test
    void shouldSkipWalletProcessingWhenNoEligibleUsers() {
        MembershipSchedulerService membershipSchedulerService = new MembershipSchedulerService(userRepository, walletService);
        ReflectionTestUtils.setField(membershipSchedulerService, "autoRenewIntervalMs", 300000L);

        when(userRepository.findByMembershipPlanAndAutoRenewMembershipTrueAndMembershipExpiryBefore(
                any(),
                any())).thenReturn(List.of());

        membershipSchedulerService.autoRenewMemberships();

        verify(walletService, never()).processAutoRenewMembership(any());
    }

    private User createEligibleUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName("Eligible User")
                .password("secret")
                .membershipPlan(User.MembershipPlan.MONTHLY)
                .autoRenewMembership(true)
                .membershipExpiry(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
