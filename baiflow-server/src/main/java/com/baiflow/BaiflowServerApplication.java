package com.baiflow;

import com.baiflow.auth.config.BaiflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(BaiflowProperties.class)
@EnableScheduling
@EnableAsync
public class BaiflowServerApplication {
    public static void main(String[] args) { SpringApplication.run(BaiflowServerApplication.class, args); }
}
