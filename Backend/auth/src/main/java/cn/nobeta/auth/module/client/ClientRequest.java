package cn.nobeta.auth.module.client;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;

/** Typed settings for the supported confidential OIDC authorization-code clients. */
public record ClientRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String clientId,
        @NotBlank @Size(max = 200) String clientName,
        @NotEmpty @Size(max = 20) Set<@NotBlank @Size(max = 1000) String> redirectUris,
        @NotNull @Size(max = 20) Set<@NotBlank @Size(max = 1000) String> postLogoutRedirectUris,
        @NotEmpty Set<@NotBlank String> scopes,
        @NotEmpty Set<@NotBlank String> authorizationGrantTypes,
        boolean requireAuthorizationConsent,
        @Min(30) @Max(600) long authorizationCodeTimeToLive,
        @Min(60) @Max(86400) long accessTokenTimeToLive,
        @Min(300) @Max(2592000) long refreshTokenTimeToLive,
        Instant clientSecretExpiresAt) {}
