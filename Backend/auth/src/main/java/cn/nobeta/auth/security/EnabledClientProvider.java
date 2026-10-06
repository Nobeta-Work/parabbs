package cn.nobeta.auth.security;

import cn.nobeta.auth.module.client.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

/** Adds an enablement policy after the framework has checked the client's credentials. */
public final class EnabledClientProvider implements AuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(EnabledClientProvider.class);
    private final AuthenticationProvider delegate;
    public EnabledClientProvider(AuthenticationProvider delegate) { this.delegate = delegate; }

    @Override public Authentication authenticate(Authentication authentication) {
        Authentication result;
        try {
            result = delegate.authenticate(authentication);
        } catch (AuthenticationException exception) {
            log.warn("Client authentication failed provider={} exceptionType={}",
                    delegate.getClass().getSimpleName(), exception.getClass().getSimpleName());
            throw exception;
        }
        if (result instanceof OAuth2ClientAuthenticationToken client && result.isAuthenticated()
                && !ClientService.isEnabled(client.getRegisteredClient())) {
            log.warn("Client authentication rejected reason=disabled");
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
        }
        if (result instanceof OAuth2ClientAuthenticationToken client && result.isAuthenticated()) {
            log.info("Client authenticated clientId={} method={}",
                    client.getRegisteredClient().getClientId(), client.getClientAuthenticationMethod().getValue());
        }
        return result;
    }
    @Override public boolean supports(Class<?> type) { return delegate.supports(type); }
}
