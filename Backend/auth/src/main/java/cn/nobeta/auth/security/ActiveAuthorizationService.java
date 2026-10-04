package cn.nobeta.auth.security;

import cn.nobeta.auth.module.account.Account;
import cn.nobeta.auth.module.account.AccountMapper;
import cn.nobeta.auth.module.client.ClientService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Keeps the JDBC model unchanged while enforcing live account and client state. */
public final class ActiveAuthorizationService implements OAuth2AuthorizationService {
    private final OAuth2AuthorizationService delegate;
    private final AccountMapper accounts;
    private final RegisteredClientRepository clients;

    public ActiveAuthorizationService(OAuth2AuthorizationService delegate, AccountMapper accounts,
            RegisteredClientRepository clients) {
        this.delegate = delegate;
        this.accounts = accounts;
        this.clients = clients;
    }

    private boolean active(OAuth2Authorization authorization) {
        Account account = accounts.findBySubject(authorization.getPrincipalName());
        return account != null && account.getStatus() == 1
                && ClientService.isEnabled(clients.findById(authorization.getRegisteredClientId()));
    }

    @Override public void save(OAuth2Authorization authorization) {
        if (!active(authorization)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
        }
        delegate.save(authorization);
    }
    @Override public void remove(OAuth2Authorization authorization) { delegate.remove(authorization); }
    @Override public OAuth2Authorization findById(String id) { return delegate.findById(id); }
    @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        OAuth2Authorization authorization = delegate.findByToken(token, type);
        return authorization != null && active(authorization) ? authorization : null;
    }
}
