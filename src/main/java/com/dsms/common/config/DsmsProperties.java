package com.dsms.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dsms")
public class DsmsProperties {
    private String timezone = "Asia/Kolkata";
    private String currency = "INR";
    private boolean requireSecrets;
    private boolean demoMode;
    private int longSessionHours = 4;
    private int reviewReminderHours = 24;
    private final Cookie cookie = new Cookie();
    private final Storage storage = new Storage();
    private final Mail mail = new Mail();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Security security = new Security();
    private final Cors cors = new Cors();

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isRequireSecrets() { return requireSecrets; }
    public void setRequireSecrets(boolean requireSecrets) { this.requireSecrets = requireSecrets; }
    public boolean isDemoMode() { return demoMode; }
    public void setDemoMode(boolean demoMode) { this.demoMode = demoMode; }
    public int getLongSessionHours() { return longSessionHours; }
    public void setLongSessionHours(int longSessionHours) { this.longSessionHours = longSessionHours; }
    public int getReviewReminderHours() { return reviewReminderHours; }
    public void setReviewReminderHours(int reviewReminderHours) { this.reviewReminderHours = reviewReminderHours; }
    public Cookie getCookie() { return cookie; }
    public Storage getStorage() { return storage; }
    public Mail getMail() { return mail; }
    public Bootstrap getBootstrap() { return bootstrap; }
    public Security getSecurity() { return security; }
    public Cors getCors() { return cors; }

    public static class Cookie {
        private boolean secure;
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
    }

    public static class Storage {
        private String directory = "./data/attachments";
        private long maxFileBytes = 10 * 1024 * 1024L;
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public long getMaxFileBytes() { return maxFileBytes; }
        public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    }

    public static class Mail {
        private boolean enabled;
        private String from = "dsms@localhost";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }

    public static class Bootstrap {
        private final Admin admin = new Admin();
        public Admin getAdmin() { return admin; }
        public static class Admin {
            private String email = "";
            private String password = "";
            private String name = "DSMS Admin";
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
    }

    public static class Security {
        private int authRateLimitWindowMinutes = 15;
        private int authRateLimitMaxFailures = 10;
        private int resetTokenHours = 2;
        private int invitationDays = 7;
        public int getAuthRateLimitWindowMinutes() { return authRateLimitWindowMinutes; }
        public void setAuthRateLimitWindowMinutes(int authRateLimitWindowMinutes) { this.authRateLimitWindowMinutes = authRateLimitWindowMinutes; }
        public int getAuthRateLimitMaxFailures() { return authRateLimitMaxFailures; }
        public void setAuthRateLimitMaxFailures(int authRateLimitMaxFailures) { this.authRateLimitMaxFailures = authRateLimitMaxFailures; }
        public int getResetTokenHours() { return resetTokenHours; }
        public void setResetTokenHours(int resetTokenHours) { this.resetTokenHours = resetTokenHours; }
        public int getInvitationDays() { return invitationDays; }
        public void setInvitationDays(int invitationDays) { this.invitationDays = invitationDays; }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
