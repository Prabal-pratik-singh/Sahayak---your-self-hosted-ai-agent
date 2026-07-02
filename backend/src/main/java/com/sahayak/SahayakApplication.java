package com.sahayak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SahayakApplication {

    public static void main(String[] args) {
        SpringApplication.run(SahayakApplication.class, args);
    }
}
