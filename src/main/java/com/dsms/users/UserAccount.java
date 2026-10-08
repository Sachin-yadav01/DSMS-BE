package com.dsms.users;

import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_account")
public class UserAccount extends AuditedEntity {
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String emailNormalized;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private String timezone;

    private String phoneCountryCode;
    private String phoneNumber;

    @Column(nullable = false)
    private boolean mustChangePassword;

    private Instant lastLoginAt;
}
