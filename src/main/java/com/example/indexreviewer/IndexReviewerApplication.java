package com.example.indexreviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IndexReviewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndexReviewerApplication.class, args);
    }
}
