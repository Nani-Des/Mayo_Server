package com.mayo.patientrecord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
        "com.mayo.patientrecord",
        "com.mayo.common.core",
        "com.mayo.common.security"
})
@EnableAsync
public class PatientRecordServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientRecordServiceApplication.class, args);
    }
}