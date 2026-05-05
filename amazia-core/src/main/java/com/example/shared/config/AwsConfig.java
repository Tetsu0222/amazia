package com.example.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class AwsConfig {

    @Bean
    @Profile("!test")
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.AP_SOUTHEAST_2)
                .build();
    }

}
