package cn.nobeta.bbs.security;

import cn.nobeta.bbs.common.constant.NameConstant;
import cn.nobeta.bbs.security.filter.AgentAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.security.filter.JwtAuthenticationFilter;
import cn.nobeta.bbs.security.handler.SecurityAccessDeniedHandler;
import cn.nobeta.bbs.security.handler.SecurityAuthenticationEntryPoint;
import cn.nobeta.bbs.security.util.PasswordEncoderImpl;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentAuthenticationFilter agentAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityAccessDeniedHandler accessDeniedHandler;
    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)     // 401
                .accessDeniedHandler(accessDeniedHandler)               // 403
            )
            .authorizeHttpRequests(auth -> auth
                // 1. 认证授权接口
                .requestMatchers(
                    "/api/auth/login", "/api/auth/register", "/api/auth/refresh"
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
                    "/api/admin/login"
                ).permitAll()
                .requestMatchers(
                    "/api/admin/**"
                ).hasRole(NameConstant.ADMIN_ROLE)
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(agentAuthenticationFilter, JwtAuthenticationFilter.class);
            

        return http.build();
    }

    // 自定义密码编码器，适配旧密码加密
    @Bean
    PasswordEncoder passwordEncoder() {
        return new PasswordEncoderImpl();
    }

    // 暴露 AuthenticationManager
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
