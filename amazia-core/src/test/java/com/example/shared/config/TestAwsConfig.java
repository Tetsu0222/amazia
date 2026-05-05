package com.example.shared.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.ses.SesClient;

@TestConfiguration
public class TestAwsConfig {

    @Bean
    @Primary
    public SesClient sesClient() {
        return Mockito.mock(SesClient.class);
    }
}
