package com.parking.smartparking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.UserVehicle;

@Repository
public interface UserVehicleRepository extends JpaRepository<UserVehicle, Long> {

    List<UserVehicle> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<UserVehicle> findByIdAndUser_Id(Long id, Long userId);

    boolean existsByUser_IdAndPlateNumberIgnoreCase(Long userId, String plateNumber);
}
