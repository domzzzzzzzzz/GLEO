package com.fbcorp.gleo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class GleoApplication {
    private static final Logger log = LoggerFactory.getLogger(GleoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(GleoApplication.class, args);
        log.info("GLEO application started");
    }
}
