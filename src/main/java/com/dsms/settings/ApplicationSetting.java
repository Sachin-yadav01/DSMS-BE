package com.dsms.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "application_setting")
@EntityListeners(AuditingEntityListener.class)
public class ApplicationSetting {
    @Id
    @Column(name = "setting_key")
    private String settingKey;

    @Column(nullable = false)
    private String valueText;

    @Column(nullable = false)
    private String valueType;

    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdOn;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant modifiedOn;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long modifiedBy;

    @Version
    private long version;
}
