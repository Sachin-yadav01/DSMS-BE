package com.dsms.settings;

import com.dsms.audit.AuditService;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Repository
interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {}

@Service
class SettingsService {
    private final ApplicationSettingRepository settings;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    SettingsService(ApplicationSettingRepository settings, CurrentUser currentUser, AuditService auditService) {
        this.settings = settings;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SettingDto> list() {
        requireAdmin();
        return settings.findAll().stream().map(SettingDto::from).toList();
    }

    @Transactional
    public SettingDto update(String key, String value) {
        requireAdmin();
        ApplicationSetting setting = settings.findById(key).orElseThrow(() -> Exceptions.notFound("Setting not found"));
        validate(setting.getValueType(), value);
        String previous = setting.getValueText();
        setting.setValueText(value);
        auditService.record("SETTING_UPDATE", "application_setting", key, Map.of("value", previous), Map.of("value", value));
        return SettingDto.from(setting);
    }

    private void validate(String type, String value) {
        try {
            switch (type) {
                case "INT" -> Integer.parseInt(value);
                case "BOOL" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new NumberFormatException();
                    }
                }
                case "DECIMAL" -> new java.math.BigDecimal(value);
                case "STRING" -> {
                    if (value.length() > 2000) {
                        throw Exceptions.badRequest("INVALID_SETTING", "Value is too long");
                    }
                }
                default -> throw Exceptions.badRequest("INVALID_SETTING", "Unsupported setting type");
            }
        } catch (NumberFormatException ex) {
            throw Exceptions.badRequest("INVALID_SETTING", "Value does not match type " + type);
        }
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
    }
}

record SettingDto(String key, String value, String type, String description) {
    static SettingDto from(ApplicationSetting setting) {
        return new SettingDto(setting.getSettingKey(), setting.getValueText(), setting.getValueType(), setting.getDescription());
    }
}

record SettingUpdateRequest(String key, String value) {}

@RestController
@RequestMapping("/api/v1/settings")
class SettingsController {
    private final SettingsService settings;

    SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public List<SettingDto> list() {
        return settings.list();
    }

    @PutMapping
    public SettingDto update(@RequestBody SettingUpdateRequest request) {
        return settings.update(request.key(), request.value());
    }
}

@Component
class SettingsSeed implements org.springframework.boot.ApplicationRunner {
    private final ApplicationSettingRepository settings;
    private final com.dsms.common.config.DsmsProperties properties;
    private final java.time.Clock clock;

    SettingsSeed(ApplicationSettingRepository settings, com.dsms.common.config.DsmsProperties properties, java.time.Clock clock) {
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        seed("business.timezone", properties.getTimezone(), "STRING", "Business timezone");
        seed("business.currency", properties.getCurrency(), "STRING", "Business currency");
        seed("long.session.hours", String.valueOf(properties.getLongSessionHours()), "INT", "Long session review threshold in hours");
        seed("review.reminder.hours", String.valueOf(properties.getReviewReminderHours()), "INT", "Hours before a candidate review reminder");
        seed("session.duplicate.running.allowed", "false", "BOOL", "Allow more than one running support session per supporter");
        seed("payment.default.model", "HOURLY", "STRING", "Default supporter payment model");
    }

    private void seed(String key, String value, String type, String description) {
        if (settings.existsById(key)) {
            return;
        }
        ApplicationSetting setting = new ApplicationSetting();
        setting.setSettingKey(key);
        setting.setValueText(value);
        setting.setValueType(type);
        setting.setDescription(description);
        setting.setCreatedOn(clock.instant());
        setting.setModifiedOn(clock.instant());
        settings.save(setting);
    }
}
