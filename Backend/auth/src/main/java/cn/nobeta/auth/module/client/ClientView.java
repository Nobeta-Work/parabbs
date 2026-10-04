package cn.nobeta.auth.module.client;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

public record ClientView(String id, String clientId, String clientName, Instant clientIdIssuedAt,
        Instant clientSecretExpiresAt, Set<String> clientAuthenticationMethods,
        Set<String> authorizationGrantTypes, Set<String> redirectUris, Set<String> postLogoutRedirectUris,
        Set<String> scopes, boolean requireProofKey, boolean requireAuthorizationConsent, boolean enabled,
        long authorizationCodeTimeToLive, long accessTokenTimeToLive, long refreshTokenTimeToLive,
        boolean reuseRefreshTokens) {
    public static ClientView from(RegisteredClient client) {
        return new ClientView(client.getId(), client.getClientId(), client.getClientName(),
                client.getClientIdIssuedAt(), client.getClientSecretExpiresAt(),
                client.getClientAuthenticationMethods().stream().map(value -> value.getValue()).collect(Collectors.toSet()),
                client.getAuthorizationGrantTypes().stream().map(value -> value.getValue()).collect(Collectors.toSet()),
                Set.copyOf(client.getRedirectUris()), Set.copyOf(client.getPostLogoutRedirectUris()),
                Set.copyOf(client.getScopes()), client.getClientSettings().isRequireProofKey(),
                client.getClientSettings().isRequireAuthorizationConsent(), ClientService.isEnabled(client),
                client.getTokenSettings().getAuthorizationCodeTimeToLive().toSeconds(),
                client.getTokenSettings().getAccessTokenTimeToLive().toSeconds(),
                client.getTokenSettings().getRefreshTokenTimeToLive().toSeconds(),
                client.getTokenSettings().isReuseRefreshTokens());
    }
}
