package cn.nobeta.auth.config;

import cn.nobeta.auth.module.account.AccountMapper;
import cn.nobeta.auth.security.ActiveAuthorizationService;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * # OAuth/OIDC 协议基础组件
 * 提供协议运行所需的存储、服务器标识及密钥配置。
 * SecurityConfig 装配处理请求的过滤器链；本类提供这些过滤器及认证提供者依赖的组件。
 * 框架配置器从 Spring 容器取得下方 Bean，无需为 authorize、token 手写 Controller。
 */
@Configuration
public class ProtocolConfig {
    /**
     * # 客户端注册信息仓库
     * 保存接入认证中心的应用配置
     * RegisteredClient 包含 client_id、客户端密钥哈希、回调地址、scope、授权类型等配置。
     * 管理接口通过仓库保存配置；协议处理组件通过它查询并校验客户端。
     * 使用框架的 JDBC 实现及 oauth2_registered_client 表，不自行实现协议数据映射。
     * @param jdbc Spring 管理的数据库访问组件。
     * @return 客户端注册信息的持久化仓库。
     */
    @Bean RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        // ! JDBC 表结构由管理员使用 infra/mysql/auth_schema.sql 手动建立；创建仓库对象本身不会建表。
        return new JdbcRegisteredClientRepository(jdbc);
    }

    /**
     * # 授权记录服务
     * 连接 authorize 与 token 两个阶段。
     * authorize 保存用户、客户端、原始请求及授权码；token 按授权码取回记录并兑换令牌。
     * 使用 PKCE 时，原始请求中的挑战值也保存在授权记录中，供兑换阶段验证。
     * 授权码及令牌状态由框架 JDBC 服务存入 oauth2_authorization 表。
     * @param jdbc 数据库访问组件。
     * @param clients 客户端仓库，用于恢复记录关联的客户端信息及检查客户端状态。
     * @param accounts 账号查询组件，用于检查授权记录对应的账号状态。
     * @return 带有项目状态检查的授权记录服务。
     */
    @Bean OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients,
            AccountMapper accounts) {
        /**
         * ? 为什么包装框架服务？
         * 保留框架的持久化实现，仅通过包装器增加实时状态约束。
         * ActiveAuthorizationService 在保存及按令牌查询时检查账号和客户端是否有效。
         * ! 该检查作用于经过此服务的请求，不会自动通知外部系统撤销已建立的登录会话。
         */
        return new ActiveAuthorizationService(new JdbcOAuth2AuthorizationService(jdbc, clients), accounts, clients);
    }

    /**
     * # 用户同意授权的记录服务
     * 使用 oauth2_authorization_consent 表。
     * 它记录某个用户已经同意某个客户端访问哪些权限范围，不保存授权码或访问令牌。
     * 框架结合客户端的 requireAuthorizationConsent 设置及已有记录决定是否展示授权确认页。
     * @param jdbc 数据库访问组件。
     * @param clients 客户端仓库，用于读取同意记录关联的客户端配置。
     * @return 框架提供的 JDBC 授权同意记录服务。
     */
    @Bean OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc,
            RegisteredClientRepository clients) {
        // * authorize 流程查询已有同意记录；用户提交同意结果后由框架保存记录。
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    /**
     * # 授权服务器标识与协议端点设置
     * issuer 是认证中心对外公布的身份标识，例如 https://auth.example.com/auth。
     * Discovery 元数据及 JWT 的 iss 声明使用它；客户端据此识别预期的认证中心。
     * 这里显式配置 issuer，避免仅根据一次请求的地址推断对外标识。
     * @param properties 从 auth 配置项绑定的项目配置。
     * @return 授权服务器设置，未显式修改的端点路径使用框架默认值。
     */
    @Bean AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        // 解析 URI，以便在启动时检查协议、主机及不允许出现的地址组成部分。
        var issuer = java.net.URI.create(properties.issuer());
        /**
         * 要求包含主机，且不包含查询参数、片段或 userInfo（URI 中的用户名、密码部分）。
         * ! 默认只允许 HTTPS；显式开启 allowLocalHttp 时，允许列出的本机地址使用 HTTP。
         * issuer 可以包含路径，因此部署在 /auth 下时可以保留这个上下文路径。
         */
        if (issuer.getHost() == null || issuer.getQuery() != null || issuer.getFragment() != null
                || issuer.getUserInfo() != null || (!"https".equals(issuer.getScheme())
                && !(properties.allowLocalHttp() && "http".equals(issuer.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(issuer.getHost())))) {
            // 配置不符合约束时终止 Bean 创建，使问题在启动阶段暴露。
            throw new IllegalArgumentException("A public HTTPS issuer is required");
        }
        // * 仅覆盖 issuer；authorize、token、JWKS 等端点路径继续使用框架默认设置。
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    /**
     * # JWT 签名与验证的密钥来源
     * 为框架提供 JWK（JSON Web Key）格式的密钥。
     * 从已有 PKCS12 密钥库读取密钥，不在每次启动时随机生成，避免重启后无法验证旧令牌。
     * 活动别名提供私钥及公钥用于当前签名；其他 RSA 证书仅提供公钥用于验证旧签名。
     * @param properties 包含密钥库位置、密码和活动别名的项目配置。
     * @param resources Spring 资源加载器，支持配置指定的资源位置，如 file: 或 classpath:。
     * @return 启动时加载的密钥集合，框架根据算法和 kid 等条件从中选择密钥。
     * @throws Exception 密钥库读取、解密或密钥检查失败时向启动过程传播异常。
     */
    @Bean JWKSource<SecurityContext> jwkSource(AuthProperties properties, ResourceLoader resources) throws Exception {
        // 取得 auth.keys 配置；密钥库密码和私钥密码分别用于下方两个读取步骤。
        var settings = properties.keys();
        // * PKCS12 是存放私钥、证书等条目的密钥库格式，不是 JWT 签名算法。
        KeyStore store = KeyStore.getInstance("PKCS12");
        // 按配置打开资源；try-with-resources 确保加载完成或发生异常后关闭输入流。
        try (InputStream input = resources.getResource(settings.location()).getInputStream()) {
            // 使用密钥库密码加载整个库；此时尚未通过私钥密码取得签名私钥。
            store.load(input, settings.storePassword().toCharArray());
        }
        // 活动别名标识当前签名条目；证书包含它对应的公钥。
        var certificate = store.getCertificate(settings.activeAlias());
        // 使用私钥密码读取活动条目的密钥，供后续 JWT 签名使用。
        var key = store.getKey(settings.activeAlias(), settings.keyPassword().toCharArray());
        // 必须同时具备证书、RSA 公钥和 RSA 私钥；模式匹配将符合类型的对象绑定为变量。
        if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)
                || !(key instanceof RSAPrivateKey privateKey)) {
            // 缺失条目或密钥类型错误时拒绝启动，不创建不完整的签名密钥来源。
            throw new IllegalStateException("The active alias must contain an RSA private key and certificate");
        }
        // ! 公私钥的 RSA 模数必须相同，活动密钥的模数至少为 2048 位。
        if (!publicKey.getModulus().equals(privateKey.getModulus()) || publicKey.getModulus().bitLength() < 2048) {
            // 检查不通过时拒绝使用这组密钥签发令牌。
            throw new IllegalStateException("The RSA signing key must match its certificate and contain at least 2048 bits");
        }
        // 收集活动签名密钥和保留的验证公钥，稍后统一构成 JWK Set。
        var keys = new ArrayList<JWK>();
        /**
         * # 构造活动签名密钥
         * 将活动公私钥转换为标准 RSA JWK。
         * kid 使用活动别名，与 SecurityConfig 的 tokenCustomizer 写入的 JWT 头保持一致。
         * SIGNATURE 声明密钥用途为签名；实际签名算法由令牌及框架配置决定。
         */
        keys.add(new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(settings.activeAlias())
                .keyUse(KeyUse.SIGNATURE).build());
        // 枚举库内所有条目，收集除活动条目以外的 RSA 证书公钥。
        var aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            // 取得当前条目名称，后续将它作为该公钥的 kid。
            String alias = aliases.nextElement();
            // 只读取证书，不读取非活动条目的私钥。
            var oldCertificate = store.getCertificate(alias);
            // 跳过已加入的活动条目、没有证书的条目和非 RSA 证书。
            if (!alias.equals(settings.activeAlias()) && oldCertificate != null
                    && oldCertificate.getPublicKey() instanceof RSAPublicKey oldPublic) {
            // * 仅添加公钥：可以验证该 kid 的旧签名，但不能用于签发新令牌。
                keys.add(new RSAKey.Builder(oldPublic).keyID(alias).keyUse(KeyUse.SIGNATURE).build());
            }
        }
        /**
         * ? 框架如何选择密钥，轮换后旧令牌怎么办？
         * JWKSet 汇总密钥；ImmutableJWKSet 按框架传入的选择条件返回匹配项。
         * ! 内部集合包含活动私钥，但框架 JWKS 端点只发布公开密钥材料。
         * ! 这是启动时的固定快照，修改密钥库文件不会自动重新加载此 Bean。
         * 轮换时保留旧公钥，可让尚未过期的旧 JWT 继续通过签名验证。
         */
        return new ImmutableJWKSet<>(new JWKSet(keys));
    }

    /**
     * # 认证中心的 JWT 解码器
     * 通过上面的密钥来源验证 JWT 签名。
     * OIDC UserInfo 等框架组件需要此 Bean；它不是向 BBS 返回令牌的生成器。
     * ! BBS 作为独立 OIDC 客户端，还需要自己的 ID Token 验证配置。
     * @param source 包含活动公私钥及保留公钥的密钥来源。
     * @return 框架构建的 JWT 解码及验证组件。
     */
    @Bean JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) {
        // 复用框架提供的构建方法，不自行实现 JWT 解析及签名验证。
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
    }
}
