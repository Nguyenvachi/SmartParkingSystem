package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.MembershipPurchaseRequest;
import com.parking.smartparking.dto.request.WalletTopUpRequest;
import com.parking.smartparking.dto.request.WalletWithdrawRequest;
import com.parking.smartparking.dto.response.WalletSummaryResponse;
import com.parking.smartparking.dto.response.WalletTransactionResponse;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.entity.WalletTransaction;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Value("${app.wallet.minimum-topup:10000}")
    private BigDecimal minimumTopUp;

    @Value("${app.wallet.minimum-withdraw:1000}")
    private BigDecimal minimumWithdraw;

    @Value("${app.membership.monthly-fee:300000}")
    private BigDecimal monthlyMembershipFee;

    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary(String email) {
        User user = getUserByEmail(email);
        return toWalletSummary(user);
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getWalletTransactions(String email) {
        return walletTransactionRepository.findByUser_EmailOrderByCreatedAtDesc(email).stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Transactional
    public WalletSummaryResponse topUp(String email, WalletTopUpRequest request) {
        User user = getUserByEmailForUpdate(email);
        BigDecimal amount = request.getAmount();

        if (amount.compareTo(minimumTopUp) < 0) {
            throw new RuntimeException("Số tiền nạp tối thiểu là " + minimumTopUp.toPlainString() + " VND.");
        }

        BigDecimal newBalance = user.getWalletBalance().add(amount);
        user.setWalletBalance(newBalance);
        userRepository.save(user);

        createTransaction(
                user,
                WalletTransaction.TransactionType.TOP_UP,
                amount,
                newBalance,
                request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : "Nạp tiền vào ví Smart Parking");

        return toWalletSummary(user);
    }

    @Transactional
    public WalletSummaryResponse withdraw(String email, WalletWithdrawRequest request) {
        User user = getUserByEmailForUpdate(email);
        BigDecimal amount = request.getAmount();

        if (amount.compareTo(minimumWithdraw) < 0) {
            throw new RuntimeException("Số tiền rút tối thiểu là " + minimumWithdraw.toPlainString() + " VND.");
        }

        ensureSufficientBalance(user, amount, "Số dư ví không đủ để rút tiền.");

        BigDecimal newBalance = user.getWalletBalance().subtract(amount);
        user.setWalletBalance(newBalance);
        userRepository.save(user);

        createTransaction(
                user,
                WalletTransaction.TransactionType.WITHDRAWAL,
                amount.negate(),
                newBalance,
                request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : "Rút tiền từ ví Smart Parking");

        return toWalletSummary(user);
    }

    @Transactional
    public WalletSummaryResponse purchaseMembership(String email, MembershipPurchaseRequest request) {
        User user = getUserByEmailForUpdate(email);
        user.setAutoRenewMembership(Boolean.TRUE.equals(request.getAutoRenewMembership()));

        renewMembership(user, WalletTransaction.TransactionType.MEMBERSHIP_PURCHASE, "Mua/gia hạn vé tháng");
        return toWalletSummary(user);
    }

    @Transactional
    public void chargeForParking(User user, BigDecimal amount, String description) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }

        User lockedUser = getUserByIdForUpdate(user.getId());

        ensureSufficientBalance(lockedUser, amount, "Số dư ví không đủ để check-out. Vui lòng nạp thêm tiền.");

        BigDecimal newBalance = lockedUser.getWalletBalance().subtract(amount);
        lockedUser.setWalletBalance(newBalance);
        userRepository.save(lockedUser);

        createTransaction(
                lockedUser,
                WalletTransaction.TransactionType.PARKING_PAYMENT,
                amount.negate(),
                newBalance,
                description);

        syncWalletState(user, lockedUser);
    }

    @Transactional
    public boolean processAutoRenewMembership(User user) {
        User lockedUser = getUserByIdForUpdate(user.getId());

        if (lockedUser.getMembershipPlan() != User.MembershipPlan.MONTHLY
                || !Boolean.TRUE.equals(lockedUser.getAutoRenewMembership())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (lockedUser.getMembershipExpiry() != null && lockedUser.getMembershipExpiry().isAfter(now)) {
            return false;
        }

        if (lockedUser.getWalletBalance().compareTo(monthlyMembershipFee) < 0) {
            return false;
        }

        renewMembership(lockedUser, WalletTransaction.TransactionType.MEMBERSHIP_RENEWAL, "Tự động gia hạn vé tháng");
        syncWalletState(user, lockedUser);
        return true;
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyMembershipFee() {
        return monthlyMembershipFee;
    }

    private void renewMembership(User user, WalletTransaction.TransactionType type, String description) {
        ensureSufficientBalance(user, monthlyMembershipFee, "Số dư ví không đủ để mua/gia hạn vé tháng.");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime = user.getMembershipExpiry() != null && user.getMembershipExpiry().isAfter(now)
                ? user.getMembershipExpiry()
                : now;

        BigDecimal newBalance = user.getWalletBalance().subtract(monthlyMembershipFee);
        user.setWalletBalance(newBalance);
        user.setMembershipPlan(User.MembershipPlan.MONTHLY);
        user.setMembershipExpiry(baseTime.plusDays(30));
        userRepository.save(user);

        createTransaction(
                user,
                type,
                monthlyMembershipFee.negate(),
                newBalance,
                description + " đến " + user.getMembershipExpiry());
    }

    private void ensureSufficientBalance(User user, BigDecimal amount, String message) {
        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException(message);
        }
    }

    private WalletSummaryResponse toWalletSummary(User user) {
        user = normalizePhase4State(user);

        List<WalletTransactionResponse> recentTransactions = walletTransactionRepository
                .findByUser_EmailOrderByCreatedAtDesc(user.getEmail())
                .stream()
                .limit(10)
                .map(this::toTransactionResponse)
                .toList();

        return WalletSummaryResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .walletBalance(user.getWalletBalance())
                .membershipPlan(user.getMembershipPlan().name())
                .membershipExpiry(user.getMembershipExpiry())
                .autoRenewMembership(user.getAutoRenewMembership())
                .monthlyMembershipFee(monthlyMembershipFee)
                .recentTransactions(recentTransactions)
                .build();
    }

    private WalletTransactionResponse toTransactionResponse(WalletTransaction transaction) {
        return WalletTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private void createTransaction(
            User user,
            WalletTransaction.TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description) {
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(user)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description(description)
                .build());
    }

    private void syncWalletState(User target, User source) {
        if (target == null || source == null) {
            return;
        }

        target.setWalletBalance(source.getWalletBalance());
        target.setMembershipPlan(source.getMembershipPlan());
        target.setMembershipExpiry(source.getMembershipExpiry());
        target.setAutoRenewMembership(source.getAutoRenewMembership());
    }

    private User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));

        return normalizePhase4State(user);
    }

    private User getUserByEmailForUpdate(String email) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));

        return normalizePhase4State(user);
    }

    private User getUserByIdForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user #" + userId));

        return normalizePhase4State(user);
    }

    private User normalizePhase4State(User user) {
        boolean changed = false;

        if (user.getWalletBalance() == null) {
            user.setWalletBalance(BigDecimal.ZERO);
            changed = true;
        }

        if (user.getMembershipPlan() == null) {
            user.setMembershipPlan(User.MembershipPlan.NONE);
            changed = true;
        }

        if (user.getAutoRenewMembership() == null) {
            user.setAutoRenewMembership(false);
            changed = true;
        }

        if (changed) {
            return userRepository.save(user);
        }

        return user;
    }
}
