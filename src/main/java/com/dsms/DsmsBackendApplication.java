package com.dsms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableMethodSecurity
@ConfigurationPropertiesScan
public class DsmsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DsmsBackendApplication.class, args);
	}
}
