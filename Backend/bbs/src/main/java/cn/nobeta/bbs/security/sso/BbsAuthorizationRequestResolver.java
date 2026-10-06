package cn.nobeta.bbs.security.sso;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 协议参数由框架生成，只补充本地返回目标及管理员入口意图。 */
@Slf4j
public final class BbsAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    public static final String RETURN_TO = "BBS_SSO_RETURN_TO";
    public static final String ADMIN = "BBS_SSO_ADMIN";
    public static final String USER_ID = "BBS_SSO_USER_ID";
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public BbsAuthorizationRequestResolver(ClientRegistrationRepository clients) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/auth/sso/authorize");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        var authorization = delegate.resolve(request);
        if ((request.getContextPath() + "/api/auth/sso/authorize/auth").equals(request.getRequestURI())) {
            log.info("BBS SSO entry method={} contextPath={} authorizationResolved={}",
                    request.getMethod(), request.getContextPath(), authorization != null);
        }
        return remember(request, authorization);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return remember(request, delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization == null) return null;
        boolean admin = "true".equals(request.getParameter("admin"));
        String destination = request.getParameter("returnTo");
        if (destination == null || destination.length() > 1024 || !destination.startsWith("/")
                || destination.startsWith("//") || destination.contains("\\")
                || destination.chars().anyMatch(Character::isISOControl)
                || destination.startsWith("/login") || destination.startsWith("/register")
                || destination.startsWith("/admin/login")) {
            destination = admin ? "/admin" : "/";
        }
        var session = request.getSession(true);
        session.setAttribute(RETURN_TO, destination);
        session.setAttribute(ADMIN, admin);
        session.removeAttribute(USER_ID);
        log.info("BBS authorization request prepared clientId={} pkcePresent={}", authorization.getClientId(),
                authorization.getAdditionalParameters().containsKey("code_challenge"));
        return authorization;
    }
}
