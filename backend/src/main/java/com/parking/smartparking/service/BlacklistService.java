package com.parking.smartparking.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.BlacklistVehicleRequest;
import com.parking.smartparking.dto.response.BlacklistedVehicleResponse;
import com.parking.smartparking.entity.BlacklistedVehicle;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BlacklistedVehicleRepository;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private static final String GLOBAL_BRANCH = "ALL";
    private static final String DEFAULT_BRANCH = "MAIN";

    private final BlacklistedVehicleRepository blacklistedVehicleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BlacklistedVehicleResponse> getVisibleBlacklist(String requesterEmail) {
        User requester = findRequester(requesterEmail);
        List<BlacklistedVehicle> vehicles = isBranchAdmin(requester)
                ? blacklistedVehicleRepository.findVisibleByBranchCode(normalizeBranchCode(requester.getBranchCode()))
                : blacklistedVehicleRepository.findAll();

        return vehicles.stream().map(this::toResponse).toList();
    }

    @Transactional
    @SuppressWarnings("null")
    public BlacklistedVehicleResponse createEntry(BlacklistVehicleRequest request, String requesterEmail) {
        User requester = findRequester(requesterEmail);
        String branchCode = resolveWritableBranch(request.getBranchCode(), requester);

        BlacklistedVehicle vehicle = BlacklistedVehicle.builder()
                .plateNumber(normalizePlate(request.getPlateNumber()))
                .branchCode(branchCode)
                .reason(request.getReason())
                .createdBy(requesterEmail)
                .active(true)
                .build();

        return toResponse(Objects.requireNonNull(blacklistedVehicleRepository.save(vehicle)));
    }

    @Transactional
    public void deactivateEntry(Long id, String requesterEmail) {
        User requester = findRequester(requesterEmail);
        Long requiredId = Objects.requireNonNull(id, "id không được null");
        BlacklistedVehicle vehicle = blacklistedVehicleRepository.findById(requiredId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy blacklist entry #" + id));

        if (isBranchAdmin(requester)
                && !normalizeBranchCode(vehicle.getBranchCode()).equals(normalizeBranchCode(requester.getBranchCode()))) {
            throw new RuntimeException("Admin chi nhánh chỉ được thao tác với blacklist của chi nhánh mình.");
        }

        vehicle.setActive(false);
        blacklistedVehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public void assertVehicleAllowed(String vehiclePlate, String branchCode) {
        if (vehiclePlate == null || vehiclePlate.isBlank()) {
            return;
        }

        String normalizedPlate = normalizePlate(vehiclePlate);
        String normalizedBranch = normalizeBranchCode(branchCode);
        List<BlacklistedVehicle> matchedVehicles = blacklistedVehicleRepository.findActiveByPlateNumberAndBranch(
                normalizedPlate,
                normalizedBranch);

        if (!matchedVehicles.isEmpty()) {
            BlacklistedVehicle vehicle = matchedVehicles.get(0);
            throw new RuntimeException("Xe biển số " + vehiclePlate + " đang nằm trong blacklist"
                    + (vehicle.getReason() != null && !vehicle.getReason().isBlank() ? ": " + vehicle.getReason() : "."));
        }
    }

    private User findRequester(String requesterEmail) {
        return userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy admin thực hiện thao tác: " + requesterEmail));
    }

    private boolean isBranchAdmin(User user) {
        return user.isBranchAdmin() && user.getBranchCode() != null && !user.getBranchCode().isBlank();
    }

    private String resolveWritableBranch(String requestedBranchCode, User requester) {
        if (isBranchAdmin(requester)) {
            String requesterBranch = normalizeBranchCode(requester.getBranchCode());
            if (requestedBranchCode != null && !requestedBranchCode.isBlank()
                    && !requesterBranch.equals(normalizeBranchCode(requestedBranchCode))) {
                throw new RuntimeException("Admin chi nhánh không được tạo blacklist cho chi nhánh khác.");
            }
            return requesterBranch;
        }

        if (requestedBranchCode == null || requestedBranchCode.isBlank()) {
            return GLOBAL_BRANCH;
        }
        return normalizeBranchCode(requestedBranchCode);
    }

    private String normalizeBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return DEFAULT_BRANCH;
        }
        return branchCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePlate(String plateNumber) {
        return plateNumber.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private BlacklistedVehicleResponse toResponse(BlacklistedVehicle vehicle) {
        return BlacklistedVehicleResponse.builder()
                .id(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .branchCode(vehicle.getBranchCode())
                .reason(vehicle.getReason())
                .active(vehicle.getActive())
                .createdBy(vehicle.getCreatedBy())
                .createdAt(vehicle.getCreatedAt())
                .build();
    }
}
