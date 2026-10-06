package cn.nobeta.bbs.security.sso;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import cn.nobeta.bbs.common.constant.NameConstant;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.config.properties.SsoProperties;
import cn.nobeta.bbs.module.auth.service.SsoIdentityService;
import lombok.RequiredArgsConstructor;

/** 回调不返回令牌到 URL，短期会话只负责把验证结果交给同源前端。 */
@Component
@RequiredArgsConstructor
public class SsoLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final SsoIdentityService identities;
    private final SsoProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession(false);
        try {
            if (session == null || !(authentication.getPrincipal() instanceof OidcUser oidc)
                    || !properties.issuer().equals(oidc.getIssuer().toString())) {
                reject(request, response, authentication, "failed");
                return;
            }
            var local = identities.resolve(oidc.getIssuer().toString(), oidc.getSubject());
            if (Boolean.TRUE.equals(session.getAttribute(BbsAuthorizationRequestResolver.ADMIN))
                    && !local.getRoles().contains(NameConstant.ADMIN_ROLE)) {
                reject(request, response, authentication, "forbidden");
                return;
            }
            session.setAttribute(BbsAuthorizationRequestResolver.USER_ID, local.getUser().getId());
            session.setMaxInactiveInterval(120);
            response.sendRedirect(properties.callbackPage());
        } catch (BusinessException failure) {
            reject(request, response, authentication, "failed");
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication, String reason) throws IOException {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        response.sendRedirect(properties.callbackPage() + "?error=" + reason);
    }
}
