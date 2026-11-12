package com.training.fraudcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudCapstoneApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudCapstoneApplication.class, args);
    }
}
