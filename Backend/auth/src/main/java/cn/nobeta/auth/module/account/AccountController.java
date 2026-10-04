package cn.nobeta.auth.module.account;

import cn.nobeta.auth.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    public record CsrfView(String headerName, String parameterName, String token) {}
    @GetMapping("/api/csrf") CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/api/accounts/register") @ResponseStatus(HttpStatus.CREATED)
    AccountService.AccountView register(@Valid @RequestBody AccountService.RegisterRequest request) {
        return accounts.register(request);
    }

    @GetMapping("/api/accounts/me") AccountService.AccountView me(Authentication principal) {
        return accounts.me(principal.getName());
    }

    @PutMapping("/api/accounts/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(Authentication principal, @Valid @RequestBody AccountService.PasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        accounts.changePassword(principal.getName(), body);
        new SecurityContextLogoutHandler().logout(request, response, principal);
    }

    @GetMapping("/api/admin/accounts") PageResponse<AccountService.AccountView> page(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accounts.page(page, size);
    }
    public record StatusRequest(@NotNull Boolean enabled) {}
    @PutMapping("/api/admin/accounts/{id}/status") AccountService.AccountView status(
            @PathVariable @Positive long id, @Valid @RequestBody StatusRequest body, Authentication principal) {
        return accounts.status(id, body.enabled(), principal.getName());
    }
}
