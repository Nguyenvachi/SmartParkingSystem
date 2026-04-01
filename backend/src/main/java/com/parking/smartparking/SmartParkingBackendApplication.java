package com.parking.smartparking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.parking.smartparking.config.DotenvSupport;

/**
 * Entry point của Ứng dụng Spring Boot
 *
 * @EnableScheduling: Bật tính năng Scheduler (Tech Key #2) Kích hoạt @Scheduled
 * trong BookingSchedulerService.java
 */
@SpringBootApplication
@EnableScheduling // Tech Key #2: Spring Scheduler - Auto-cancel expired bookings
public class SmartParkingBackendApplication {

    public static void main(String[] args) {
        DotenvSupport.loadLocalDotenvIfPresent();
        SpringApplication.run(SmartParkingBackendApplication.class, args);
    }

}
