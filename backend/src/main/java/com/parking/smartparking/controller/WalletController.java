package com.parking.smartparking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parking.smartparking.dto.request.MembershipPurchaseRequest;
import com.parking.smartparking.dto.request.WalletTopUpRequest;
import com.parking.smartparking.dto.request.WalletWithdrawRequest;
import com.parking.smartparking.dto.response.WalletSummaryResponse;
import com.parking.smartparking.dto.response.WalletTransactionResponse;
import com.parking.smartparking.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<WalletSummaryResponse> getWalletSummary(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(walletService.getWalletSummary(email));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getWalletTransactions(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(walletService.getWalletTransactions(email));
    }

    @PostMapping("/top-up")
    public ResponseEntity<WalletSummaryResponse> topUp(
            @Valid @RequestBody WalletTopUpRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(walletService.topUp(email, request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WalletSummaryResponse> withdraw(
            @Valid @RequestBody WalletWithdrawRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(walletService.withdraw(email, request));
    }

    @PostMapping("/membership")
    public ResponseEntity<WalletSummaryResponse> purchaseMembership(
            @Valid @RequestBody MembershipPurchaseRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(walletService.purchaseMembership(email, request));
    }
}
