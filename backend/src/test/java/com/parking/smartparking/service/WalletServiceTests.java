package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.parking.smartparking.dto.request.MembershipPurchaseRequest;
import com.parking.smartparking.dto.request.WalletTopUpRequest;
import com.parking.smartparking.dto.request.WalletWithdrawRequest;
import com.parking.smartparking.dto.response.WalletSummaryResponse;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.entity.WalletTransaction;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unused"})
class WalletServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(userRepository, walletTransactionRepository);
        ReflectionTestUtils.setField(walletService, "minimumTopUp", new BigDecimal("10000"));
        ReflectionTestUtils.setField(walletService, "minimumWithdraw", new BigDecimal("1000"));
        ReflectionTestUtils.setField(walletService, "monthlyMembershipFee", new BigDecimal("300000"));
    }

    @Test
    void shouldTopUpUsingLockedUserRecord() {
        User user = createUser(1L, "wallet@test.com", new BigDecimal("100000"));
        WalletTopUpRequest request = new WalletTopUpRequest();
        request.setAmount(new BigDecimal("50000"));
        request.setDescription("Nap test");

        when(userRepository.findByEmailForUpdate("wallet@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletTransactionRepository.findByUser_EmailOrderByCreatedAtDesc("wallet@test.com"))
                .thenReturn(List.of());

        WalletSummaryResponse response = walletService.topUp("wallet@test.com", request);

        assertEquals(new BigDecimal("150000"), user.getWalletBalance());
        assertEquals(new BigDecimal("150000"), response.getWalletBalance());
        verify(userRepository).save(user);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void shouldWithdrawUsingLockedUserRecord() {
        User user = createUser(2L, "withdraw@test.com", new BigDecimal("120000"));
        WalletWithdrawRequest request = new WalletWithdrawRequest();
        request.setAmount(new BigDecimal("20000"));
        request.setDescription("Rut test");

        when(userRepository.findByEmailForUpdate("withdraw@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletTransactionRepository.findByUser_EmailOrderByCreatedAtDesc("withdraw@test.com"))
                .thenReturn(List.of());

        WalletSummaryResponse response = walletService.withdraw("withdraw@test.com", request);

        assertEquals(new BigDecimal("100000"), user.getWalletBalance());
        assertEquals(new BigDecimal("100000"), response.getWalletBalance());
        verify(userRepository).save(user);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void shouldRejectWithdrawWhenBalanceIsInsufficient() {
        User user = createUser(3L, "low@test.com", new BigDecimal("15000"));
        WalletWithdrawRequest request = new WalletWithdrawRequest();
        request.setAmount(new BigDecimal("20000"));

        when(userRepository.findByEmailForUpdate("low@test.com")).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> walletService.withdraw("low@test.com", request));

        assertEquals("Số dư ví không đủ để rút tiền.", exception.getMessage());
    }

    @Test
    void shouldChargeParkingUsingFreshLockedUserRecord() {
        User staleUser = createUser(4L, "charge@test.com", new BigDecimal("10000"));
        User lockedUser = createUser(4L, "charge@test.com", new BigDecimal("200000"));

        when(userRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(lockedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        walletService.chargeForParking(staleUser, new BigDecimal("50000"), "Thanh toan booking");

        assertEquals(new BigDecimal("150000"), lockedUser.getWalletBalance());
        assertEquals(new BigDecimal("150000"), staleUser.getWalletBalance());
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void shouldPurchaseMembershipWithAutoRenewEnabled() {
        User user = createUser(5L, "member@test.com", new BigDecimal("500000"));
        MembershipPurchaseRequest request = new MembershipPurchaseRequest();
        request.setPlan("MONTHLY");
        request.setAutoRenewMembership(true);

        when(userRepository.findByEmailForUpdate("member@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletTransactionRepository.findByUser_EmailOrderByCreatedAtDesc("member@test.com"))
                .thenReturn(List.of());

        WalletSummaryResponse response = walletService.purchaseMembership("member@test.com", request);

        assertEquals(User.MembershipPlan.MONTHLY.name(), response.getMembershipPlan());
        assertTrue(response.getAutoRenewMembership());
        assertEquals(new BigDecimal("200000"), response.getWalletBalance());
        assertTrue(response.getMembershipExpiry().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void shouldAutoRenewMembershipUsingLockedUserRecord() {
        User schedulerUser = createUser(6L, "renew@test.com", new BigDecimal("450000"));
        schedulerUser.setMembershipPlan(User.MembershipPlan.MONTHLY);
        schedulerUser.setAutoRenewMembership(true);
        schedulerUser.setMembershipExpiry(LocalDateTime.now().minusMinutes(2));

        User lockedUser = createUser(6L, "renew@test.com", new BigDecimal("450000"));
        lockedUser.setMembershipPlan(User.MembershipPlan.MONTHLY);
        lockedUser.setAutoRenewMembership(true);
        lockedUser.setMembershipExpiry(LocalDateTime.now().minusMinutes(2));

        when(userRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(lockedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean renewed = walletService.processAutoRenewMembership(schedulerUser);

        assertTrue(renewed);
        assertEquals(new BigDecimal("150000"), lockedUser.getWalletBalance());
        assertEquals(new BigDecimal("150000"), schedulerUser.getWalletBalance());
        assertTrue(schedulerUser.getMembershipExpiry().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void shouldSkipAutoRenewWhenBalanceTooLow() {
        User schedulerUser = createUser(7L, "renew-low@test.com", new BigDecimal("100000"));
        schedulerUser.setMembershipPlan(User.MembershipPlan.MONTHLY);
        schedulerUser.setAutoRenewMembership(true);
        schedulerUser.setMembershipExpiry(LocalDateTime.now().minusMinutes(5));

        User lockedUser = createUser(7L, "renew-low@test.com", new BigDecimal("100000"));
        lockedUser.setMembershipPlan(User.MembershipPlan.MONTHLY);
        lockedUser.setAutoRenewMembership(true);
        lockedUser.setMembershipExpiry(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(lockedUser));

        boolean renewed = walletService.processAutoRenewMembership(schedulerUser);

        assertFalse(renewed);
        verify(walletTransactionRepository, org.mockito.Mockito.never()).save(any(WalletTransaction.class));
    }

    private User createUser(Long id, String email, BigDecimal balance) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName("Wallet Test")
                .password("secret")
                .walletBalance(balance)
                .membershipPlan(User.MembershipPlan.NONE)
                .autoRenewMembership(false)
                .build();
    }
}
