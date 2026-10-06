package cn.nobeta.bbs.security.sso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** BBS 首期只使用认证结果，不持久保存 Auth 的访问或刷新令牌。 */
public final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,
            Authentication principal, HttpServletRequest request) {
        return null;
    }
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
    }
    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
    }
}
