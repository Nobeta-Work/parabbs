package cn.nobeta.auth.security;

import cn.nobeta.auth.module.account.Account;
import cn.nobeta.auth.module.account.AccountMapper;
import cn.nobeta.auth.module.client.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Keeps the JDBC model unchanged while enforcing live account and client state. */
public final class ActiveAuthorizationService implements OAuth2AuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(ActiveAuthorizationService.class);
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
            log.warn("Authorization save rejected reason=inactive_account_or_client");
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
        }
        delegate.save(authorization);
        log.info("Authorization persisted subject={} registeredClientId={} grantType={} codePresent={} accessTokenPresent={} idTokenPresent={}",
                authorization.getPrincipalName(), authorization.getRegisteredClientId(),
                authorization.getAuthorizationGrantType().getValue(),
                authorization.getToken(OAuth2AuthorizationCode.class) != null,
                authorization.getAccessToken() != null,
                authorization.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class) != null);
    }
    @Override public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        log.info("Authorization removed subject={} registeredClientId={}",
                authorization.getPrincipalName(), authorization.getRegisteredClientId());
    }
    @Override public OAuth2Authorization findById(String id) { return delegate.findById(id); }
    @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        OAuth2Authorization authorization = delegate.findByToken(token, type);
        boolean usable = authorization != null && active(authorization);
        log.info("Authorization lookup tokenType={} found={} accountAndClientActive={}",
                type == null ? "unspecified" : type.getValue(), authorization != null, usable);
        return usable ? authorization : null;
    }
}
