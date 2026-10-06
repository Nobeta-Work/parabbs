package cn.nobeta.bbs.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OIDC 对外地址由部署配置提供；密钥使用 Spring OAuth2 Client 配置。 */
@ConfigurationProperties(prefix = "para.sso")
public record SsoProperties(String issuer, String callbackPage) {
    public SsoProperties {
        var address = java.net.URI.create(issuer);
        String host = address.getHost();
        boolean localHttp = "http".equals(address.getScheme())
                && ("localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host));
        if (host == null || !("https".equals(address.getScheme()) || localHttp)
                || address.getUserInfo() != null || address.getQuery() != null || address.getFragment() != null
                || issuer.length() > 255 || !java.nio.charset.StandardCharsets.US_ASCII.newEncoder().canEncode(issuer)
                || issuer.endsWith("/")) {
            throw new IllegalArgumentException("BBS Auth issuer 必须是准确的 HTTPS 地址或本地 HTTP 地址，且不以 / 结尾");
        }
        if (callbackPage == null || !callbackPage.startsWith("/bbs/") || callbackPage.contains("\\")
                || callbackPage.contains("?") || callbackPage.contains("#")
                || callbackPage.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("BBS 登录完成页必须是本站 /bbs/ 下的路径");
        }
    }
}
