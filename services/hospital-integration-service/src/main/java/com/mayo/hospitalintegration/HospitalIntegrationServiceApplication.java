package com.mayo.hospitalintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableIntegration
@EnableKafka
@EnableAsync
@ComponentScan(basePackages = {"com.mayo.hospitalintegration", "com.mayo.common"})
public class HospitalIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalIntegrationServiceApplication.class, args);
    }
}