package com.dsms.common.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyRunner implements ApplicationRunner {
    private final DsmsProperties properties;

    public ProductionSafetyRunner(DsmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRequireSecrets()) {
            return;
        }
        if (properties.isDemoMode()) {
            throw new IllegalStateException("Demo mode is not allowed when secrets are required");
        }
        if (!StringUtils.hasText(properties.getBootstrap().getAdmin().getEmail())
                || !StringUtils.hasText(properties.getBootstrap().getAdmin().getPassword())) {
            throw new IllegalStateException("Critical bootstrap secrets are missing");
        }
    }
}
