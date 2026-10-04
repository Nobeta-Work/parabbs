package cn.nobeta.auth.module.client;

import cn.nobeta.auth.common.PageResponse;
import cn.nobeta.auth.config.AuthProperties;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {
    public static final String ENABLED = "para.client.enabled";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final ProtocolMapper queries;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;

    public ClientService(RegisteredClientRepository clients, OAuth2AuthorizationService authorizations,
            ProtocolMapper queries, PasswordEncoder encoder, AuthProperties properties) {
        this.clients = clients;
        this.authorizations = authorizations;
        this.queries = queries;
        this.encoder = encoder;
        this.properties = properties;
    }

    public record SecretResponse(ClientView client, String clientSecret) {
        @Override public String toString() { return "SecretResponse[client=" + client.clientId() + ", secret redacted]"; }
    }

    public static boolean isEnabled(RegisteredClient client) {
        return client != null && !Boolean.FALSE.equals(client.getClientSettings().getSetting(ENABLED));
    }

    @Transactional public SecretResponse create(ClientRequest request) {
        if (clients.findByClientId(request.clientId()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client ID already exists");
        }
        String secret = newSecret();
        var client = configure(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.clientId()).clientIdIssuedAt(Instant.now())
                .clientSecret(encoder.encode(secret)), request, true).build();
        clients.save(client);
        audit("create", client);
        return new SecretResponse(ClientView.from(client), secret);
    }

    public ClientView detail(String id) { return ClientView.from(required(id)); }

    @Transactional(readOnly = true)
    public PageResponse<ClientView> page(String query, Boolean enabled, int page, int size) {
        String normalized = query == null || query.isBlank() ? null : query.strip();
        String status = enabled == null ? null : enabled.toString();
        var items = queries.pageClients(normalized, status, size, (long) (page - 1) * size).stream()
                .map(this::required).map(ClientView::from).toList();
        return new PageResponse<>(items, queries.countClients(normalized, status), page, size);
    }

    @Transactional public ClientView update(String id, ClientRequest request) {
        lock(id);
        var previous = required(id);
        if (!previous.getClientId().equals(request.clientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client ID is immutable");
        }
        var current = configure(RegisteredClient.from(previous), request, isEnabled(previous)).build();
        clients.save(current);
        audit("update", current);
        return ClientView.from(current);
    }

    @Transactional public ClientView status(String id, boolean enabled) {
        lock(id);
        var previous = required(id);
        if (isEnabled(previous) == enabled) return ClientView.from(previous);
        var settings = ClientSettings.withSettings(previous.getClientSettings().getSettings())
                .setting(ENABLED, enabled).build();
        var current = RegisteredClient.from(previous).clientSettings(settings).build();
        clients.save(current);
        if (!enabled) {
            for (String authorizationId : queries.authorizationsByClient(id)) {
                var authorization = authorizations.findById(authorizationId);
                if (authorization != null) authorizations.remove(authorization);
            }
        }
        audit(enabled ? "enable" : "disable", current);
        return ClientView.from(current);
    }

    @Transactional public SecretResponse resetSecret(String id) {
        lock(id);
        var previous = required(id);
        String secret = newSecret();
        var current = RegisteredClient.from(previous).clientSecret(encoder.encode(secret)).build();
        clients.save(current);
        audit("reset-secret", current);
        return new SecretResponse(ClientView.from(current), secret);
    }

    private RegisteredClient.Builder configure(RegisteredClient.Builder builder, ClientRequest request, boolean enabled) {
        if (!request.scopes().equals(Set.of("openid"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the implemented openid scope is supported");
        }
        if (!request.authorizationGrantTypes().contains("authorization_code")
                || !Set.of("authorization_code", "refresh_token").containsAll(request.authorizationGrantTypes())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported grant configuration");
        }
        request.redirectUris().forEach(this::checkUri);
        request.postLogoutRedirectUris().forEach(this::checkUri);
        checkSerializedLength(request.redirectUris(), 1000);
        checkSerializedLength(request.postLogoutRedirectUris(), 1000);
        if (request.clientSecretExpiresAt() != null && !request.clientSecretExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret expiry must be in the future");
        }
        return builder.clientName(request.clientName()).clientSecretExpiresAt(request.clientSecretExpiresAt())
                .clientAuthenticationMethods(values -> { values.clear(); values.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC); })
                .authorizationGrantTypes(values -> {
                    values.clear();
                    request.authorizationGrantTypes().forEach(value -> values.add(new AuthorizationGrantType(value)));
                })
                .redirectUris(values -> { values.clear(); values.addAll(request.redirectUris()); })
                .postLogoutRedirectUris(values -> { values.clear(); values.addAll(request.postLogoutRedirectUris()); })
                .scopes(values -> { values.clear(); values.addAll(request.scopes()); })
                // 当前注册的是机密客户端，沿用框架默认的可选 PKCE 配置。
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(request.requireAuthorizationConsent()).setting(ENABLED, enabled).build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofSeconds(request.authorizationCodeTimeToLive()))
                        .accessTokenTimeToLive(Duration.ofSeconds(request.accessTokenTimeToLive()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(request.refreshTokenTimeToLive()))
                        .reuseRefreshTokens(false).accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256).build());
    }

    private void checkUri(String value) {
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid callback URI"); }
        boolean local = properties.allowLocalHttp() && "http".equals(uri.getScheme()) && uri.getHost() != null
                && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost());
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || value.contains("*") || value.contains(",")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callbacks must be complete HTTPS URIs without fragments or wildcards");
        }
    }

    private void checkSerializedLength(Set<String> values, int limit) {
        if (String.join(",", values).length() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback list exceeds storage capacity");
        }
    }
    private RegisteredClient required(String id) {
        var client = clients.findById(id);
        if (client == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        return client;
    }
    private void lock(String id) {
        if (queries.lockClient(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
    }
    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private void audit(String action, RegisteredClient client) {
        var principal = SecurityContextHolder.getContext().getAuthentication();
        log.info("Client management action={} clientId={} actor={}", action, client.getClientId(),
                principal == null ? "bootstrap" : principal.getName());
    }
}
