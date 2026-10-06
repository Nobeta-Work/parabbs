package cn.nobeta.bbs.security;

import cn.nobeta.bbs.common.constant.NameConstant;
import cn.nobeta.bbs.security.filter.AgentAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.DispatcherType;
import cn.nobeta.bbs.security.filter.JwtAuthenticationFilter;
import cn.nobeta.bbs.security.handler.SecurityAccessDeniedHandler;
import cn.nobeta.bbs.security.handler.SecurityAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import cn.nobeta.bbs.config.properties.SsoProperties;
import cn.nobeta.bbs.security.sso.BbsAuthorizationRequestResolver;
import cn.nobeta.bbs.security.sso.DiscardingAuthorizedClientRepository;
import cn.nobeta.bbs.security.sso.SsoLoginSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SsoProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AgentAuthenticationFilter agentAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityAccessDeniedHandler accessDeniedHandler;
    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;

    /** OIDC 登录才使用短期会话；业务 API 不接受这套 Cookie 身份。 */
    @Bean
    @Order(1)
    SecurityFilterChain ssoChain(HttpSecurity http, ClientRegistrationRepository clients,
            SsoLoginSuccessHandler success, SsoProperties properties) throws Exception {
        http.securityMatcher("/api/auth/sso/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/sso/authorize/**", "/api/auth/sso/callback/**", "/api/auth/sso/csrf").permitAll()
                        .requestMatchers("/api/auth/sso/session").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2Login(login -> login
                        .loginPage("/api/auth/sso/authorize/auth")
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new BbsAuthorizationRequestResolver(clients)))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/sso/callback/*"))
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .successHandler(success)
                        .failureHandler((request, response, failure) -> {
                            log.warn("BBS OIDC login failed exceptionType={}", failure.getClass().getSimpleName());
                            new SecurityContextLogoutHandler().logout(request, response, null);
                            response.setHeader("Cache-Control", "no-store");
                            response.sendRedirect(properties.callbackPage() + "?error=failed");
                        }));
        // 保留默认 CSRF；前端读取 /csrf 后才可 POST 获取业务令牌。
        log.info("BBS SSO chain configured authorizationPath=/bbs/api/auth/sso/authorize/auth");
        return http.build();
    }

    // Filter Bean 只由业务安全链运行，不让 Servlet 自动注册到 OIDC 链之外。
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtServletRegistration() {
        var registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AgentAuthenticationFilter> disableAgentServletRegistration() {
        var registration = new FilterRegistrationBean<>(agentAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)     // 401
                .accessDeniedHandler(accessDeniedHandler)               // 403
            )
            .authorizeHttpRequests(auth -> auth
                // 允许 Servlet 容器呈现原始错误，避免 /error 将 500/404 覆盖为未登录 401。
                // 只放行内部 ERROR 分派；普通请求仍受原有权限规则保护。
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // 1. 认证授权接口
                .requestMatchers(
                    "/api/auth/identity-provider", "/api/auth/refresh"
                ).permitAll()
                .requestMatchers(
                    "/api/auth/logout"
                ).hasAnyRole(NameConstant.USER_ROLE, NameConstant.ADMIN_ROLE)
                // 2. 用户接口
                .requestMatchers(
                    "/api/users/me/**"
                ).hasAnyRole(NameConstant.USER_ROLE, NameConstant.ADMIN_ROLE)
                .requestMatchers(
                    "/api/users/**"
                ).permitAll()
                // 3. 目录接口
                .requestMatchers(
                    "/api/folders/**"
                ).hasAuthority(NameConstant.PERM_FOLDER_MANAGE_SELF)
                // 4. 标签接口
                .requestMatchers(
                    HttpMethod.GET, "/api/tags"
                ).permitAll()
                .requestMatchers(
                    "/api/tags"
                ).authenticated()
                // 5. 博客接口
                .requestMatchers(
                    "/api/blogs/me/**"
                ).hasAuthority(NameConstant.PERM_BLOG_MANAGE_SELF)
                .requestMatchers(
                    HttpMethod.GET, "/api/blogs/comments/*"
                ).permitAll()
                .requestMatchers(
                    "/api/blogs/comments", "/api/blogs/comments/**"
                ).hasAnyRole(NameConstant.USER_ROLE, NameConstant.ADMIN_ROLE)
                .requestMatchers(
                    "/api/blogs/page", "/api/blogs/*"
                ).permitAll()
                // 6. 点赞接口
                .requestMatchers(
                    "/api/like/**"
                ).authenticated()
                // 7. 文件接口
                .requestMatchers(
                    "/api/images",
                    "/api/uploadAvatar",
                    "/api/uploadImage"
                ).authenticated()
                // 8. Agent 接口
                .requestMatchers(
                    "/api/agent/**"
                ).hasAnyRole(NameConstant.USER_ROLE)
                // 9. 后台管理接口
                .requestMatchers(
                    "/api/admin/**"
                ).hasRole(NameConstant.ADMIN_ROLE)
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(agentAuthenticationFilter, JwtAuthenticationFilter.class);
            

        return http.build();
    }

}
