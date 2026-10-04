package cn.nobeta.auth.security;

import cn.nobeta.auth.module.client.ClientService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

/** Adds an enablement policy after the framework has checked the client's credentials. */
public final class EnabledClientProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    public EnabledClientProvider(AuthenticationProvider delegate) { this.delegate = delegate; }

    @Override public Authentication authenticate(Authentication authentication) {
        Authentication result = delegate.authenticate(authentication);
        if (result instanceof OAuth2ClientAuthenticationToken client && result.isAuthenticated()
                && !ClientService.isEnabled(client.getRegisteredClient())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
        }
        return result;
    }
    @Override public boolean supports(Class<?> type) { return delegate.supports(type); }
}
