package com.dsms.users;

import com.dsms.audit.AuditService;
import com.dsms.auth.AuthService;
import com.dsms.auth.InvitationToken;
import com.dsms.auth.InvitationTokenRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.api.PageResponse;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import com.dsms.notifications.OutboxService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final InvitationTokenRepository invitations;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final AuthService authService;
    private final DsmsProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserAccountRepository users, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                       InvitationTokenRepository invitations, OutboxService outboxService, AuditService auditService,
                       AuthService authService, DsmsProperties properties, Clock clock) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.invitations = invitations;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.authService = authService;
        this.properties = properties;
        this.clock = clock;
    }

    public record UserResponse(String id, String email, String displayName, String role, String status, String timezone,
                               String phoneCountryCode, String phoneNumber, String temporaryPassword) {}

    public record CreateUserRequest(@Email @NotBlank String email, @NotBlank String displayName, @NotNull UserRole role,
                                    String timezone, String phoneCountryCode, String phoneNumber) {}

    public record UpdateUserRequest(String displayName, String timezone, String phoneCountryCode, String phoneNumber) {}

    public record ChangeRoleRequest(@NotNull UserRole role) {}

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        requireAdmin();
        var result = users.findAll(PageRequest.of(Math.max(page, 0), bound(size)));
        return PageResponse.of(result.stream().map(u -> toResponse(u, null)).toList(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public UserResponse get(String id) {
        UserAccount account = users.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("User not found"));
        assertCanView(account);
        return toResponse(account, null);
    }

    @Transactional
    public UserResponse invite(CreateUserRequest request) {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Not allowed to create users");
        }
        if (request.role() == UserRole.ADMIN && !currentUser.isAdmin()) {
            throw Exceptions.forbidden("Only an admin can create admin users");
        }
        String email = AuthService.normalize(request.email());
        if (users.existsByEmailNormalized(email)) {
            throw Exceptions.conflict("EMAIL_IN_USE", "A user with that email already exists");
        }
        UserAccount account = new UserAccount();
        account.setEmail(request.email().trim());
        account.setEmailNormalized(email);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String temporary = HexFormat.of().formatHex(bytes);
        account.setPasswordHash(passwordEncoder.encode(temporary));
        account.setDisplayName(request.displayName());
        account.setRole(request.role());
        boolean staffInvite = request.role() == UserRole.ADMIN || request.role() == UserRole.COORDINATOR;
        account.setStatus(staffInvite ? UserStatus.ACTIVE : UserStatus.INVITED);
        account.setTimezone(request.timezone() == null || request.timezone().isBlank() ? properties.getTimezone() : request.timezone());
        account.setPhoneCountryCode(request.phoneCountryCode());
        account.setPhoneNumber(request.phoneNumber());
        account.setMustChangePassword(true);
        users.save(account);
        String token = HexFormat.of().formatHex(bytes);
        InvitationToken invitation = new InvitationToken();
        invitation.setUserId(account.getId());
        invitation.setTokenHash(AuthService.hash(token));
        invitation.setExpiresOn(clock.instant().plus(properties.getSecurity().getInvitationDays(), ChronoUnit.DAYS));
        invitation.setCreatedOn(clock.instant());
        invitation.setCreatedBy(currentUser.requireUserId());
        invitations.save(invitation);
        outboxService.enqueue("USER_INVITE", "invite:" + account.getId(), Map.of(
                "email", account.getEmail(), "token", token, "userId", account.getId().toString(),
                "temporaryPassword", temporary));
        auditService.record("USER_INVITE", "user_account", Ids.toJson(account.getId()), null, Map.of("email", email, "role", request.role().name()));
        return toResponse(account, staffInvite ? temporary : null);
    }

    @Transactional
    public UserResponse update(String id, UpdateUserRequest request) {
        UserAccount account = users.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("User not found"));
        if (!currentUser.isAdmin() && !currentUser.requireUserId().equals(account.getId())) {
            throw Exceptions.forbidden("Cannot update this user");
        }
        if (request.displayName() != null) account.setDisplayName(request.displayName());
        if (request.timezone() != null) account.setTimezone(request.timezone());
        if (request.phoneCountryCode() != null) account.setPhoneCountryCode(request.phoneCountryCode());
        if (request.phoneNumber() != null) account.setPhoneNumber(request.phoneNumber());
        return toResponse(account, null);
    }

    @Transactional
    public UserResponse changeRole(String id, ChangeRoleRequest request) {
        requireAdmin();
        UserAccount account = users.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("User not found"));
        UserRole previous = account.getRole();
        account.setRole(request.role());
        authService.invalidateSessions(account.getEmail());
        auditService.record("USER_ROLE_CHANGE", "user_account", id, Map.of("role", previous.name()), Map.of("role", request.role().name()));
        return toResponse(account, null);
    }

    @Transactional
    public UserResponse setStatus(String id, UserStatus status) {
        requireAdmin();
        UserAccount account = users.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("User not found"));
        account.setStatus(status);
        if (status == UserStatus.SUSPENDED) {
            authService.invalidateSessions(account.getEmail());
        }
        auditService.record("USER_STATUS", "user_account", id, null, Map.of("status", status.name()));
        return toResponse(account, null);
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
    }

    private void assertCanView(UserAccount account) {
        if (currentUser.isAdmin() || currentUser.requireUserId().equals(account.getId())) {
            return;
        }
        if (currentUser.isCoordinator()) {
            return;
        }
        throw Exceptions.forbidden("Cannot view this user");
    }

    private int bound(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private UserResponse toResponse(UserAccount account, String temporaryPassword) {
        return new UserResponse(Ids.toJson(account.getId()), account.getEmail(), account.getDisplayName(),
                account.getRole().name(), account.getStatus().name(), account.getTimezone(),
                account.getPhoneCountryCode(), account.getPhoneNumber(), temporaryPassword);
    }
}
