package com.mayo.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
    "com.mayo.sync",
    "com.mayo.common.core",
    "com.mayo.common.security",
    "com.mayo.events"
})
@EnableKafka
@EnableAsync
@EnableJpaRepositories(basePackages = "com.mayo.sync.repository")
public class SyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncServiceApplication.class, args);
    }
}