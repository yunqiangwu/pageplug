package com.appsmith.server.configurations;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class CloudOSConfig {
    @Value("${pageplug.cloudos.api_baseurl}")
    String baseUrl;

    @Value("${pageplug.cloudos.mock_baseurl}")
    String mockUrl;

    @Value("${pageplug.cloudos.in_cloudos}")
    Boolean inCloudOS;

    @Value("${pageplug.cloudos.jwt_secret_key}")
    String secretKey;
}