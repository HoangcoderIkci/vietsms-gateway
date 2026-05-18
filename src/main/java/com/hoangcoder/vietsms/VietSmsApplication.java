package com.hoangcoder.vietsms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VietSmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(VietSmsApplication.class, args);
    }
}
