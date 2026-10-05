package cn.nobeta.auth.config;

import cn.nobeta.auth.module.client.ClientService;
import cn.nobeta.auth.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.*;

/**
 * 安全配置入口：注册认证组件，并分别配置协议端点和普通应用接口。
 */
@Configuration
public class SecurityConfig {
    /**
     * 注册密码编码器：新密码采用框架编码格式，旧 BBS 密码哈希用于迁移兼容。
     */
    @Bean PasswordEncoder passwordEncoder() { return new MigratingPasswordEncoder(); } // 创建项目密码编码器，供认证及账号业务使用。

    /**
     * 使用 DaoAuthenticationProvider 完成用户名、密码认证。
     * users 加载账号并返回 UserDetails，即框架使用的账号、密码哈希、权限及状态信息。
     * encoder 比较用户提交的密码与存储的哈希。
     * 认证成功后，如果密码编码需要升级，UserDetailsPasswordService 负责保存新哈希。
     */
    @Bean DaoAuthenticationProvider passwordAuthenticationProvider(AccountDetailsService users, PasswordEncoder encoder) { // 方法参数由 Spring 容器注入。
        var provider = new DaoAuthenticationProvider(users); // 指定账号加载服务，保留框架的密码认证及账号状态检查。
        provider.setPasswordEncoder(encoder); // 设置密码匹配方式，避免在 Controller 中手动比较密码。
        provider.setUserDetailsPasswordService(users); // 接入认证成功后的密码编码升级，不是普通修改密码接口。
        return provider; // 将配置完成的认证提供者注册为 Bean。
    }

    /** 创建会话账号过滤器，用于检查已有用户会话中的账号状态并刷新权限。 */
    @Bean AccountSessionFilter accountSessionFilter(AccountDetailsService users) {
        return new AccountSessionFilter(users); // 具体状态检查及会话处理逻辑由该过滤器实现。
    }

    /**
     * Boot 可能把 Filter Bean 自动注册到 Servlet 容器。
     * 本项目通过 addFilterBefore 将它加入 Security 链，因此关闭容器的独立注册，
     * 让过滤器的执行范围和位置由安全链控制，避免在链外重复执行。
     */
    @Bean FilterRegistrationBean<AccountSessionFilter> disableServletRegistration(AccountSessionFilter filter) { // 配置已有过滤器的 Servlet 注册行为。
        var registration = new FilterRegistrationBean<>(filter); // 创建该过滤器的注册配置。
        registration.setEnabled(false); // 禁用独立 Servlet 注册；不影响它作为安全链内过滤器执行。
        return registration; // Boot 根据此 Bean 决定是否注册过滤器。
    } // 结束 Servlet 注册配置。

    /**
     * JWT 是框架签发的令牌格式；kid 是 JWT 头中的签名密钥标识。
     * 客户端根据 kid 从 JWKS（公开验证密钥集合）中选择公钥验证签名。
     * 此处仅设置活动密钥标识，JWT 内容构造及签名仍由框架完成。
     */
    @Bean OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(AuthProperties properties) { // 注册 JWT 生成阶段调用的定制器。
        return context -> context.getJwsHeader().keyId(properties.keys().activeAlias()); // 将当前活动密钥别名写入 kid，供签名密钥选择及客户端验签使用。
    } // 结束 JWT 定制器的定义。

    /**
     * # OAuth/OIDC 协议链
     * OAuth 处理应用获得授权；OIDC 在其基础上提供标准的用户身份认证。
     * @param http 当前链的安全配置构建器
     * @param sessionFilter 已有账号会话的状态及权限刷新过滤器
     * @return 构建完成的协议安全过滤器链。
     * @throws Exception 构建失败时向启动过程传播异常。
     */
    @Bean @Order(1) // 注册优先匹配的链，只有协议端点请求才进入此链。
    SecurityFilterChain protocolChain(HttpSecurity http, AccountSessionFilter sessionFilter) throws Exception {
        // 创建 Spring Security 框架的 OAuth 授权服务器配置器
        var server = OAuth2AuthorizationServerConfigurer.authorizationServer(); 
        /**
         * securityMatcher 决定整条链处理哪些请求
         * getEndpointsMatcher 返回框架封装的协议端点匹配器
         */
        http.securityMatcher(server.getEndpointsMatcher())
                // 应用授权服务器配置器，通过回调设置协议相关扩展
                .with(server, authorization -> authorization
                        // 验证访问客户端
                        .clientAuthentication(clients -> clients.authenticationProviders(providers ->
                                providers.replaceAll(EnabledClientProvider::new)))
                        // 配置授权端点使用的认证提供者
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
                             // 遍历该端点的认证提供者
                            for (var provider : providers) {
                                // 仅扩展授权码请求的认证提供者
                                if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider code) {
                                    /**
                                     * 先执行框架默认授权请求验证
                                     * 验证 redirect_url、scope
                                     */
                                    code.setAuthenticationValidator(new OAuth2AuthorizationCodeRequestAuthenticationValidator()
                                            // 默认验证成功后执行下方追加检查。
                                            .andThen(context -> {
                                                // 取得本次授权请求的认证对象 (不是已签发的访问令牌)
                                                var token = (OAuth2AuthorizationCodeRequestAuthenticationToken) context.getAuthentication(); 
                                                // ? 启停检查
                                                // 检查本次请求对应的已注册客户端是否被停用
                                                if (!ClientService.isEnabled(context.getRegisteredClient())) { 
                                                    // 拒绝请求，并交由框架授权端点错误处理器处理。
                                                    throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                                                            // 使用标准 unauthorized_client 错误码，附带本次请求对象。
                                                            new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT), token); 
                                                }
                                                // PKCE 参数及算法交由框架默认校验，不额外要求每次授权请求使用 PKCE。
                                            }));
                                }
                            }
                        }))
                        /**
                         * # 显式启用 OIDC 默认能力
                         * - 让客户端发现认证中心的 OIDC 配置
                         * - 允许通过访问令牌获取用户身份信息
                         * - 处理客户端发起的标准退出请求
                         * - 在符合 OIDC 授权码流程中返回 id_token
                         */
                        .oidc(Customizer.withDefaults()))
                /**
                 * 此权限规则处理经过协议过滤器后仍继续执行的请求。
                 * Discovery、JWKS 等公开端点由各自协议过滤器处理，
                 * 不能由下面的 authenticated 推断它们也要求用户登录。
                 */
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                // 为满足指定匹配条件的未认证请求设置登录入口。
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor( 
                        // 将需要登录的浏览器请求引导至认证中心登录页。
                        new LoginUrlAuthenticationEntryPoint("/login"), 
                        // 按请求接受的媒体类型匹配 HTML 请求，不表示所有协议错误都跳转登录。
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                        // 启用认证上下文自动保存模式，持久化会话认证身份的变化。
                .securityContext(context -> context.requireExplicitSave(false)) 
                // 在填充匿名身份前插入会话检查
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 普通应用链：处理账号登录、账号业务及管理员接口。
     * 本链没有指定 securityMatcher，因此承接未匹配前一条协议链的请求。
     * @param http 本链的安全配置构建器。
     * @param provider 用户名、密码认证提供者。
     * @param sessionFilter 已有账号会话的状态及权限刷新过滤器。
     * @param json 用于序列化 API 错误响应的 Jackson 对象。
     * @return 构建完成的普通应用安全过滤器链。
     * @throws Exception 构建失败时向启动过程传播异常。
     */
    @Bean @Order(2) // 匹配优先级低于协议链；请求不会先执行协议链再执行本链。
    SecurityFilterChain applicationChain(HttpSecurity http, DaoAuthenticationProvider provider, // 注入构建器及账号密码认证提供者。
            AccountSessionFilter sessionFilter, ObjectMapper json) throws Exception { // 注入会话过滤器和 JSON 序列化器。
        var api = new AntPathRequestMatcher("/api/**"); // 创建 API 路径匹配器，供未认证错误处理器选择使用。
        http.authenticationProvider(provider) // 将账号密码认证提供者加入本链的认证管理器。
                .authorizeHttpRequests(requests -> requests // 配置本链内请求的访问权限，规则按声明顺序匹配。
                        .requestMatchers("/login", "/error", "/api/csrf", "/api/accounts/register").permitAll() // 允许未登录访问这些入口；permitAll 不会关闭 CSRF 校验。
                        .requestMatchers("/api/admin/**").hasRole("AUTH_ADMIN") // 要求 ROLE_AUTH_ADMIN 权限；hasRole 自动添加 ROLE_ 前缀。
                        .requestMatchers("/api/accounts/me", "/api/accounts/me/password").authenticated() // 当前账号查询和修改密码要求有效登录身份。
                        .anyRequest().denyAll()) // 拒绝没有被上述规则明确允许的其他请求。
                .formLogin(login -> login // 使用前端登录页，账号密码认证仍交给框架过滤器。
                        .loginPage("/login") // GET /auth/login 由同源前端提供，不生成默认登录页。
                        .loginProcessingUrl("/api/login") // POST /auth/api/login 接收原生表单，与页面路径分离。
                        .failureUrl("/login?error") // 认证失败后返回前端登录页。
                        .defaultSuccessUrl("/account", false) // 优先恢复保存的授权请求，否则进入前端账号页。
                        .permitAll()) // 允许匿名访问登录入口，仍保留 CSRF 校验。
                .logout(logout -> logout // 普通会话退出与 OIDC /connect/logout 分属不同配置。
                        .logoutUrl("/api/logout") // POST /auth/api/logout 处理退出，GET /auth/logout 是前端确认页。
                        .logoutSuccessUrl("/login?logout")) // 退出后返回前端登录页。
                .exceptionHandling(exceptions -> exceptions // 配置未认证及访问拒绝时的响应。
                        .defaultAuthenticationEntryPointFor((request, response, exception) -> { // 定义下方 API 匹配器对应的未认证请求处理器。
                            response.setStatus(401); // 401 表示请求缺少有效认证身份。
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE); // 使用 application/problem+json 标准错误响应类型。
                            json.writeValue(response.getOutputStream(), ProblemDetail.forStatus(401)); // 将 Spring 的 Problem Details 错误对象写入响应体。
                        }, api) // 将上方未认证处理器绑定到 /api/** 请求。
                        .accessDeniedHandler((request, response, exception) -> { // 处理本链的访问拒绝，例如权限不足或 CSRF 校验失败。
                            response.setStatus(403); // 403 表示请求被访问控制拒绝。
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE); // 指定响应体采用标准 Problem Details JSON 格式。
                            json.writeValue(response.getOutputStream(), ProblemDetail.forStatus(403)); // 输出访问拒绝错误对象，不暴露内部异常详情。
                        })) // 结束访问拒绝回调及异常处理配置。
                .securityContext(context -> context.requireExplicitSave(false)) // 自动保存认证上下文；Redis Session 的存储接入由其他配置负责。
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class); // 在填充匿名身份前检查已有会话的账号状态并刷新权限。
        return http.build(); // 构建普通应用链，保留未显式关闭的 CSRF 等框架默认保护。
    } // 结束普通应用链定义。
} // 结束安全配置类。
