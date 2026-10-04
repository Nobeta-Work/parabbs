package cn.nobeta.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth")
public record AuthProperties(
        @NotBlank String issuer,
        boolean allowLocalHttp,
        boolean registrationEnabled,
        @NotNull @Valid Keys keys,
        @NotNull @Valid Bootstrap bootstrap) {
    public record Keys(@NotBlank String location, @NotBlank String storePassword,
                       @NotBlank String keyPassword, @NotBlank String activeAlias) {
        @Override public String toString() { return "Keys[credentials redacted]"; }
    }
    public record Bootstrap(boolean enabled, String username, String password) {
        @Override public String toString() { return "Bootstrap[credentials redacted]"; }
    }
}
