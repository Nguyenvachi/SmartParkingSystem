package com.parking.smartparking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipSchedulerService {

    private final UserRepository userRepository;
    private final WalletService walletService;

    @Value("${app.membership.auto-renew-interval-ms:300000}")
    private long autoRenewIntervalMs;

    @Scheduled(fixedDelayString = "${app.membership.auto-renew-interval-ms:300000}")
    public void autoRenewMemberships() {
        LocalDateTime now = LocalDateTime.now();
        List<User> eligibleUsers = userRepository
                .findByMembershipPlanAndAutoRenewMembershipTrueAndMembershipExpiryBefore(
                        User.MembershipPlan.MONTHLY,
                        now.plusSeconds(1));

        if (eligibleUsers.isEmpty()) {
            log.debug("🔁 Membership scheduler: Không có user cần auto-renew lúc {}", now);
            return;
        }

        int renewedCount = 0;
        for (User user : eligibleUsers) {
            if (walletService.processAutoRenewMembership(user)) {
                renewedCount++;
                log.info("🔁 Auto-renew thành công cho user {} | expiry mới = {}",
                        user.getEmail(), user.getMembershipExpiry());
            } else {
                log.warn("🔁 Auto-renew thất bại cho user {} do ví không đủ số dư", user.getEmail());
            }
        }

        log.info("🔁 Membership scheduler chạy mỗi {} ms: gia hạn thành công {}/{} user",
                autoRenewIntervalMs, renewedCount, eligibleUsers.size());
    }
}
