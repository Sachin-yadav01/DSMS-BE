package com.dsms.auth;

import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import com.dsms.common.security.DsmsUserPrincipal;
import com.dsms.notifications.OutboxService;
import com.dsms.users.UserAccount;
import com.dsms.users.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final AuthenticationAttemptRepository attempts;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final DsmsProperties properties;
    private final Clock clock;
    private final OutboxService outboxService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            AuthenticationManager authenticationManager,
            UserAccountRepository users,
            AuthenticationAttemptRepository attempts,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            CurrentUser currentUser,
            DsmsProperties properties,
            Clock clock,
            OutboxService outboxService,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.attempts = attempts;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
        this.outboxService = outboxService;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public AuthDtos.CurrentUserResponse register(AuthDtos.RegisterRequest request, HttpServletRequest http, HttpServletResponse response) {
        if (request.role() != UserRole.CANDIDATE && request.role() != UserRole.EXPERT) {
            throw Exceptions.badRequest("INVALID_ROLE", "Self-registration is limited to Candidate or Expert. An admin can later grant Coordinator access.");
        }
        String email = normalize(request.email());
        if (users.existsByEmailNormalized(email)) {
            throw Exceptions.conflict("EMAIL_IN_USE", "A user with that email already exists");
        }
        UserAccount account = new UserAccount();
        account.setEmail(request.email().trim());
        account.setEmailNormalized(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setDisplayName(request.displayName().trim());
        account.setRole(request.role());
        account.setStatus(UserStatus.ACTIVE);
        account.setTimezone(request.timezone() == null || request.timezone().isBlank()
                ? properties.getTimezone()
                : request.timezone().trim());
        account.setMustChangePassword(false);
        users.save(account);
        return login(new AuthDtos.LoginRequest(request.email(), request.password()), http, response);
    }

    @Transactional
    public AuthDtos.CurrentUserResponse login(AuthDtos.LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        String email = normalize(request.email());
        Instant now = clock.instant();
        Instant since = now.minus(properties.getSecurity().getAuthRateLimitWindowMinutes(), ChronoUnit.MINUTES);
        if (attempts.countRecentFailures(email, since) >= properties.getSecurity().getAuthRateLimitMaxFailures()) {
            throw Exceptions.conflict("RATE_LIMITED", "Too many sign-in attempts. Try again later.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
            recordAttempt(email, clientIp(http), true, now);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            HttpSession existing = http.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }
            http.getSession(true);
            http.changeSessionId();
            securityContextRepository.saveContext(context, http, response);
            UserAccount account = users.findByEmailNormalized(email).orElseThrow();
            account.setLastLoginAt(now);
            return toCurrentUser(account);
        } catch (BadCredentialsException ex) {
            recordAttempt(email, clientIp(http), false, now);
            throw Exceptions.unauthorized("Invalid email or password");
        }
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        currentUser.principal().ifPresent(principal -> invalidateSessions(principal.getUsername()));
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Transactional(readOnly = true)
    public AuthDtos.CurrentUserResponse me() {
        return toCurrentUser(currentUser.requireAccount());
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        UserAccount account = currentUser.requireAccount();
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw Exceptions.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        invalidateSessions(account.getEmail());
    }

    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        String email = normalize(request.email());
        Instant now = clock.instant();
        Instant since = now.minus(properties.getSecurity().getAuthRateLimitWindowMinutes(), ChronoUnit.MINUTES);
        if (attempts.countRecentFailures("reset:" + email, since) >= properties.getSecurity().getAuthRateLimitMaxFailures()) {
            return;
        }
        recordAttempt("reset:" + email, null, false, now);
        users.findByEmailNormalized(email).ifPresent(user -> {
            if (user.getStatus() != UserStatus.ACTIVE) {
                return;
            }
            String token = randomToken();
            PasswordResetToken entity = new PasswordResetToken();
            entity.setUserId(user.getId());
            entity.setTokenHash(hash(token));
            entity.setExpiresOn(now.plus(properties.getSecurity().getResetTokenHours(), ChronoUnit.HOURS));
            entity.setCreatedOn(now);
            resetTokens.save(entity);
            outboxService.enqueue("PASSWORD_RESET", "password-reset:" + user.getId() + ":" + entity.getExpiresOn().toEpochMilli(),
                    Map.of("userId", user.getId().toString(), "email", user.getEmail(), "token", token));
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> Exceptions.badRequest("INVALID_TOKEN", "Reset link is invalid or expired"));
        if (token.getUsedOn() != null || token.getExpiresOn().isBefore(now)) {
            throw Exceptions.badRequest("INVALID_TOKEN", "Reset link is invalid or expired");
        }
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> Exceptions.badRequest("INVALID_TOKEN", "Reset link is invalid or expired"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        token.setUsedOn(now);
        invalidateSessions(user.getEmail());
    }

    public void invalidateSessions(String email) {
        sessionRepository.findByPrincipalName(email).forEach((id, session) -> sessionRepository.deleteById(id));
    }

    public static AuthDtos.CurrentUserResponse toCurrentUser(UserAccount account) {
        return new AuthDtos.CurrentUserResponse(
                Ids.toJson(account.getId()),
                account.getEmail(),
                account.getDisplayName(),
                account.getRole().name(),
                account.getStatus().name(),
                account.getTimezone(),
                account.isMustChangePassword());
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void recordAttempt(String email, String ip, boolean success, Instant now) {
        AuthenticationAttempt attempt = new AuthenticationAttempt();
        attempt.setEmailNormalized(email);
        attempt.setIpAddress(ip);
        attempt.setSuccess(success);
        attempt.setAttemptedOn(now);
        attempts.save(attempt);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
