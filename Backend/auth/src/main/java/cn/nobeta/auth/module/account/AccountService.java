package cn.nobeta.auth.module.account;

import cn.nobeta.auth.common.PageResponse;
import cn.nobeta.auth.config.AuthProperties;
import cn.nobeta.auth.module.client.ProtocolMapper;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
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
                              LocalDateTime createTime, LocalDateTime updateTime) {
        static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getSubject(), account.getUsername(),
                    account.getStatus() == 1, account.getCreateTime(), account.getUpdateTime());
        }
    }

    @Transactional public AccountView register(RegisterRequest request) {
        if (!properties.registrationEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        Account account = create(request.username(), request.password());
        return AccountView.from(accounts.findById(account.getId()));
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
        return account;
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    public AccountView me(String subject) { return AccountView.from(requiredSubject(subject)); }

    @Transactional public void changePassword(String subject, PasswordRequest request) {
        var account = requiredSubject(subject);
        if (account.getStatus() != 1 || !passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        checkPassword(request.newPassword());
        if (accounts.updatePassword(account.getId(), passwords.encode(request.newPassword()), account.getPasswordHash()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credentials changed concurrently");
        }
        revoke(subject);
    }

    @Transactional public AccountView status(long id, boolean enabled, String actorSubject) {
        var account = accounts.findById(id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        if (!enabled && actorSubject.equals(account.getSubject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An administrator cannot disable the active account");
        }
        accounts.updateStatus(id, enabled ? 1 : 0);
        if (!enabled) revoke(account.getSubject());
        return AccountView.from(accounts.findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountView> page(int page, int size) {
        return new PageResponse<>(accounts.page(size, (long) (page - 1) * size).stream()
                .map(AccountView::from).toList(), accounts.count(), page, size);
    }

    @Transactional public void bootstrapAdministrator(String username, String password) {
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
    }

    private Account requiredSubject(String subject) {
        var account = accounts.findBySubject(subject);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return account;
    }

    private void revoke(String subject) {
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
