package com.mayo.deviceregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for Device Registry Service
 */
@SpringBootApplication(scanBasePackages = {
    "com.mayo.deviceregistry",
    "com.mayo.common.core",
    "com.mayo.common.security"
})
@EnableJpaRepositories(basePackages = "com.mayo.deviceregistry.repository")
public class DeviceRegistryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceRegistryServiceApplication.class, args);
    }
}