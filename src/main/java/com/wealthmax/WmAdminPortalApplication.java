package com.wealthmax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WmAdminPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmAdminPortalApplication.class, args);
    }

}
