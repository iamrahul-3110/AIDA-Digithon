package com.aida;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AidaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AidaApplication.class, args);
    }
}
