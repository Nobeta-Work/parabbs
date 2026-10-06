package cn.nobeta.bbs.module.auth.controller;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import cn.nobeta.bbs.common.constant.NameConstant;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.result.Result;
import cn.nobeta.bbs.config.properties.SsoProperties;
import cn.nobeta.bbs.module.auth.service.AuthService;
import cn.nobeta.bbs.module.auth.service.SsoIdentityService;
import cn.nobeta.bbs.module.auth.vo.TokenVO;
import cn.nobeta.bbs.security.sso.BbsAuthorizationRequestResolver;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SsoController {
    private final AuthService auth;
    private final SsoIdentityService identities;
    private final StringRedisTemplate redis;
    private final SsoProperties properties;

    public record CsrfView(String headerName, String parameterName, String token) {}
    public record LoginResult(TokenVO tokens, String returnTo) {}
    public record IdentityProvider(String loginUrl, String registerUrl, String passwordUrl) {}

    @GetMapping("/api/auth/identity-provider")
    public Result<IdentityProvider> provider(HttpServletRequest request) {
        return Result.success(new IdentityProvider(request.getContextPath() + "/api/auth/sso/authorize/auth",
                properties.issuer() + "/register", properties.issuer() + "/password"));
    }

    @GetMapping("/api/auth/sso/csrf")
    public Result<CsrfView> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken()));
    }

    @PostMapping("/api/auth/sso/session")
    public Result<LoginResult> finish(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(BbsAuthorizationRequestResolver.USER_ID) instanceof Long userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录交接已过期，请重新登录");
        }
        try {
            // 多节点下也只允许交接一次；这不是 OAuth 授权码或新的登录凭证。
            String key = "para:bbs:sso:consumed:" + session.getId();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10)))) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录交接已被使用");
            }
            var local = identities.load(userId);
            if (Boolean.TRUE.equals(session.getAttribute(BbsAuthorizationRequestResolver.ADMIN))
                    && !local.getRoles().contains(NameConstant.ADMIN_ROLE)) {
                throw new BusinessException(ResultCode.ADMIN_FORBIDDEN);
            }
            String returnTo = (String) session.getAttribute(BbsAuthorizationRequestResolver.RETURN_TO);
            return Result.success(new LoginResult(auth.issue(local), returnTo == null ? "/" : returnTo));
        } finally {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
    }
}
