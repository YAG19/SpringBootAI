package com.example.springbootai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class SpringBootAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootAiApplication.class, args);
    }
}
