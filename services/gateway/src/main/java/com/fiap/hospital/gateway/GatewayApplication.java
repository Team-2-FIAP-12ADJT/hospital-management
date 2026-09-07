package com.fiap.hospital.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import com.fiap.hospital.gateway.proxy.GatewayProxyFilter;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    @Bean
    public FilterRegistrationBean<GatewayProxyFilter> gatewayProxyFilterRegistration(
        GatewayProxyFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-99);
        registration.addUrlPatterns("/*");
        return registration;
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
