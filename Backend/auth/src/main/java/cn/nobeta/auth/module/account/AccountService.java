package cn.nobeta.auth.module.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.nobeta.auth.common.PageResponse;
import cn.nobeta.auth.config.AuthProperties;
import cn.nobeta.auth.module.client.ProtocolMapper;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final AuthProperties properties;
    private final OAuth2AuthorizationService authorizations;
    private final ProtocolMapper protocols;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public AccountService(AccountMapper accounts, PasswordEncoder passwords, AuthProperties properties,
            OAuth2AuthorizationService authorizations, ProtocolMapper protocols,
            FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.properties = properties;
        this.authorizations = authorizations;
        this.protocols = protocols;
        this.sessions = sessions;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String username,
            @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "RegisterRequest[password redacted]"; }
    }
    public record PasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {
        @Override public String toString() { return "PasswordRequest[credentials redacted]"; }
    }
    public record AccountView(long id, String subject, String username, boolean enabled,
                              LocalDateTime createTime, LocalDateTime updateTime, List<String> roles) {
        static AccountView from(Account account, List<String> roles) {
            return new AccountView(account.getId(), account.getSubject(), account.getUsername(),
                    account.getStatus() == 1, account.getCreateTime(), account.getUpdateTime(), roles);
        }
    }

    private AccountView view(Account account) { return AccountView.from(account, accounts.roles(account.getId())); }

    @Transactional public AccountView register(RegisterRequest request) {
        if (!properties.registrationEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        log.info("Account registration accepted");
        Account account = create(request.username(), request.password());
        return view(accounts.findById(account.getId()));
    }

    private Account create(String username, String password) {
        username = normalizeUsername(username);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username");
        }
        checkPassword(password);
        var account = new Account();
        account.setSubject(UUID.randomUUID().toString());
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(password));
        account.setStatus(1);
        accounts.insert(account);
        log.info("Account created accountId={} subject={}", account.getId(), account.getSubject());
        return account;
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    public AccountView me(String subject) { return view(requiredSubject(subject)); }

    @Transactional public void changePassword(String subject, PasswordRequest request) {
        log.info("Password change started subject={}", subject);
        var account = requiredSubject(subject);
        if (account.getStatus() != 1 || !passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        checkPassword(request.newPassword());
        if (accounts.updatePassword(account.getId(), passwords.encode(request.newPassword()), account.getPasswordHash()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credentials changed concurrently");
        }
        revoke(subject);
        log.info("Password changed subject={}", subject);
    }

    @Transactional public AccountView status(long id, boolean enabled, String actorSubject) {
        requireAdministrator(actorSubject);
        var account = accounts.lockAccount(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        if (!enabled && actorSubject.equals(account.getSubject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An administrator cannot disable the active account");
        }
        protectLastAdministrator(account, enabled);
        log.info("Account status changing accountId={} enabled={} actor={}", id, enabled, actorSubject);
        accounts.updateStatus(id, enabled ? 1 : 0);
        if (!enabled) revoke(account.getSubject());
        return view(accounts.findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountView> page(String query, Boolean enabled, int page, int size) {
        String normalized = query == null || query.isBlank() ? null : normalizeUsername(query);
        Integer status = enabled == null ? null : enabled ? 1 : 0;
        return new PageResponse<>(accounts.search(normalized, status, size, (long) (page - 1) * size).stream()
                .map(this::view).toList(), accounts.searchCount(normalized, status), page, size);
    }

    public AccountView detail(long id) { return view(requiredId(id)); }

    @Transactional public AccountView createManaged(RegisterRequest request, String actor) {
        requireAdministrator(actor);
        var account = create(request.username(), request.password());
        log.info("Administrator created account accountId={} actor={}", account.getId(), actor);
        return view(requiredId(account.getId()));
    }

    @Transactional public AccountView roles(long id, Set<String> roles, String actor) {
        if (!Set.of("ROLE_AUTH_ADMIN").containsAll(roles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Auth role");
        }
        requireAdministrator(actor);
        var account = accounts.lockAccount(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        if (actor.equals(account.getSubject()) && !roles.contains("ROLE_AUTH_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove your own administrator role");
        }
        if (!roles.contains("ROLE_AUTH_ADMIN")) protectLastAdministrator(account, false);
        if (Set.copyOf(accounts.roles(id)).equals(roles)) return view(account);
        accounts.deleteRoles(id);
        roles.forEach(role -> accounts.insertRole(id, role));
        revoke(account.getSubject());
        log.info("Account roles replaced accountId={} roles={} actor={}", id, roles, actor);
        return view(requiredId(id));
    }

    @Transactional public void resetPassword(long id, String password, String actor) {
        requireAdministrator(actor);
        var account = accounts.lockAccount(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        if (actor.equals(account.getSubject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the personal password change endpoint");
        }
        checkPassword(password);
        if (accounts.updatePassword(id, passwords.encode(password), account.getPasswordHash()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credentials changed concurrently");
        }
        revoke(account.getSubject());
        log.info("Administrator reset password accountId={} actor={}", id, actor);
    }

    /** Serialize permission changes, then recheck the actor's live permissions in the transaction. */
    private void requireAdministrator(String actor) {
        accounts.lockAdministrators();
        var account = requiredSubject(actor);
        if (account.getStatus() != 1 || !accounts.roles(account.getId()).contains("ROLE_AUTH_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator permission required");
        }
    }

    private void protectLastAdministrator(Account account, boolean retainingAccess) {
        if (!retainingAccess && account.getStatus() == 1
                && accounts.roles(account.getId()).contains("ROLE_AUTH_ADMIN")
                && accounts.enabledAdministratorCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one enabled administrator is required");
        }
    }

    private Account requiredId(long id) {
        var account = accounts.findById(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return account;
    }

    @Transactional public void bootstrapAdministrator(String username, String password) {
        log.info("Administrator bootstrap started");
        String normalized = normalizeUsername(username);
        var existing = accounts.findByUsername(normalized);
        if (existing != null) {
            if (!accounts.roles(existing.getId()).contains("ROLE_AUTH_ADMIN")) {
                throw new IllegalStateException("Bootstrap refuses to promote an existing non-administrator account");
            }
            return;
        }
        var administrator = create(normalized, password);
        accounts.insertRole(administrator.getId(), "ROLE_AUTH_ADMIN");
        log.info("Administrator bootstrapped accountId={}", administrator.getId());
    }

    private Account requiredSubject(String subject) {
        var account = accounts.findBySubject(subject);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return account;
    }

    private void revoke(String subject) {
        log.info("Revoking account authorizations and sessions subject={}", subject);
        for (String id : protocols.authorizationsBySubject(subject)) {
            var authorization = authorizations.findById(id);
            if (authorization != null) authorizations.remove(authorization);
        }
        sessions.findByPrincipalName(subject).keySet().forEach(sessions::deleteById);
    }

    private void checkPassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
