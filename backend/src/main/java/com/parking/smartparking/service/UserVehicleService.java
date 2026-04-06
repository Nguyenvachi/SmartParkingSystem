package com.parking.smartparking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.UserVehicleCreateRequest;
import com.parking.smartparking.dto.request.UserVehicleUpdateRequest;
import com.parking.smartparking.dto.response.UserVehicleResponse;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.entity.UserVehicle;
import com.parking.smartparking.repository.UserRepository;
import com.parking.smartparking.repository.UserVehicleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserVehicleService {

    private final UserRepository userRepository;
    private final UserVehicleRepository userVehicleRepository;

    @Transactional(readOnly = true)
    public List<UserVehicleResponse> listMyVehicles(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        return userVehicleRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserVehicleResponse createVehicle(String email, UserVehicleCreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        final String plate = normalizePlate(request.getPlateNumber());
        if (userVehicleRepository.existsByUser_IdAndPlateNumberIgnoreCase(user.getId(), plate)) {
            throw new RuntimeException("Biển số đã tồn tại trong danh sách phương tiện.");
        }

        UserVehicle vehicle = UserVehicle.builder()
                .user(user)
                .plateNumber(plate)
                .vehicleType(request.getVehicleType())
                .color(safeTrim(request.getColor()))
                .build();

        return toResponse(userVehicleRepository.save(vehicle));
    }

    @Transactional
    public UserVehicleResponse updateVehicle(String email, Long id, UserVehicleUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        UserVehicle vehicle = userVehicleRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phương tiện #" + id));

        final String plate = normalizePlate(request.getPlateNumber());
        if (!vehicle.getPlateNumber().equalsIgnoreCase(plate)
                && userVehicleRepository.existsByUser_IdAndPlateNumberIgnoreCase(user.getId(), plate)) {
            throw new RuntimeException("Biển số đã tồn tại trong danh sách phương tiện.");
        }

        vehicle.setPlateNumber(plate);
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setColor(safeTrim(request.getColor()));

        return toResponse(userVehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + email));

        UserVehicle vehicle = userVehicleRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phương tiện #" + id));

        userVehicleRepository.delete(vehicle);
    }

    private UserVehicleResponse toResponse(UserVehicle v) {
        return UserVehicleResponse.builder()
                .id(v.getId())
                .plateNumber(v.getPlateNumber())
                .vehicleType(v.getVehicleType() != null ? v.getVehicleType().name() : null)
                .color(v.getColor())
                .build();
    }

    private String normalizePlate(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase();
    }

    private String safeTrim(String raw) {
        if (raw == null) {
            return null;
        }
        final String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
