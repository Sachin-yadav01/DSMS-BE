package com.dsms.auth;

import com.dsms.common.config.DsmsProperties;
import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.users.UserAccount;
import com.dsms.users.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Order(1)
public class BootstrapAdminRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DsmsProperties properties;
    private final java.time.Clock clock;

    public BootstrapAdminRunner(UserAccountRepository users, PasswordEncoder passwordEncoder, DsmsProperties properties, java.time.Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.getBootstrap().getAdmin().getEmail();
        String password = properties.getBootstrap().getAdmin().getPassword();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            if (properties.isRequireSecrets()) {
                throw new IllegalStateException("Bootstrap admin email and password are required");
            }
            log.info("Bootstrap admin skipped because credentials were not provided");
            return;
        }
        String normalized = AuthService.normalize(email);
        if (users.existsByEmailNormalized(normalized)) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setEmail(email.trim());
        admin.setEmailNormalized(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName(properties.getBootstrap().getAdmin().getName());
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTimezone(properties.getTimezone());
        admin.setMustChangePassword(false);
        admin.setCreatedOn(clock.instant());
        admin.setModifiedOn(clock.instant());
        users.save(admin);
        log.info("Bootstrap admin created for {}", normalized);
    }
}
