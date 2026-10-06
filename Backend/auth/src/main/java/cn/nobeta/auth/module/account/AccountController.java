package cn.nobeta.auth.module.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.nobeta.auth.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    public record CsrfView(String headerName, String parameterName, String token) {}
    @GetMapping("/api/csrf") CsrfView csrf(CsrfToken token) {
        log.info("Controller csrf requested");
        return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/api/accounts/register") @ResponseStatus(HttpStatus.CREATED)
    AccountService.AccountView register(@Valid @RequestBody AccountService.RegisterRequest request) {
        log.info("Controller account registration requested");
        return accounts.register(request);
    }

    @GetMapping("/api/accounts/me") AccountService.AccountView me(Authentication principal) {
        log.info("Controller account profile requested subject={}", principal.getName());
        return accounts.me(principal.getName());
    }

    @PutMapping("/api/accounts/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(Authentication principal, @Valid @RequestBody AccountService.PasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        log.info("Controller password change requested subject={}", principal.getName());
        accounts.changePassword(principal.getName(), body);
        new SecurityContextLogoutHandler().logout(request, response, principal);
    }

    @GetMapping("/api/admin/accounts") PageResponse<AccountService.AccountView> page(
            @RequestParam(required = false) @Size(max = 64) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Controller account page requested page={} size={}", page, size);
        return accounts.page(query, enabled, page, size);
    }
    @GetMapping("/api/admin/roles") List<String> roles() {
        log.info("Controller Auth roles requested");
        return List.of("ROLE_AUTH_ADMIN");
    }
    @GetMapping("/api/admin/accounts/{id}") AccountService.AccountView detail(@PathVariable @Positive long id) {
        log.info("Controller account detail requested accountId={}", id);
        return accounts.detail(id);
    }
    @PostMapping("/api/admin/accounts") @ResponseStatus(HttpStatus.CREATED)
    AccountService.AccountView create(@Valid @RequestBody AccountService.RegisterRequest body, Authentication actor) {
        log.info("Controller managed account creation requested actor={}", actor.getName());
        return accounts.createManaged(body, actor.getName());
    }
    public record RolesRequest(@NotNull @Size(max = 1) Set<@NotBlank String> roles) {}
    @PutMapping("/api/admin/accounts/{id}/roles") AccountService.AccountView roles(
            @PathVariable @Positive long id, @Valid @RequestBody RolesRequest body, Authentication actor) {
        log.info("Controller roles update requested accountId={} actor={}", id, actor.getName());
        return accounts.roles(id, body.roles(), actor.getName());
    }
    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
    }
    @PutMapping("/api/admin/accounts/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable @Positive long id, @Valid @RequestBody ResetPasswordRequest body,
            Authentication actor) {
        log.info("Controller password reset requested accountId={} actor={}", id, actor.getName());
        accounts.resetPassword(id, body.password(), actor.getName());
    }
    public record StatusRequest(@NotNull Boolean enabled) {}
    @PutMapping("/api/admin/accounts/{id}/status") AccountService.AccountView status(
            @PathVariable @Positive long id, @Valid @RequestBody StatusRequest body, Authentication principal) {
        log.info("Controller account status requested accountId={} enabled={} actor={}", id, body.enabled(), principal.getName());
        return accounts.status(id, body.enabled(), principal.getName());
    }
}
