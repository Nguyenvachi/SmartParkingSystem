package com.parking.smartparking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.BlacklistedVehicle;

@Repository
public interface BlacklistedVehicleRepository extends JpaRepository<BlacklistedVehicle, Long> {

    @Query("select vehicle from BlacklistedVehicle vehicle where vehicle.active = true and vehicle.plateNumber = :plateNumber and (vehicle.branchCode = 'ALL' or vehicle.branchCode = :branchCode)")
    List<BlacklistedVehicle> findActiveByPlateNumberAndBranch(@Param("plateNumber") String plateNumber,
            @Param("branchCode") String branchCode);

    @Query("select vehicle from BlacklistedVehicle vehicle where vehicle.active = true and (vehicle.branchCode = :branchCode or vehicle.branchCode = 'ALL') order by vehicle.createdAt desc")
    List<BlacklistedVehicle> findVisibleByBranchCode(@Param("branchCode") String branchCode);
}
