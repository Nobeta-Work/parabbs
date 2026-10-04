package cn.nobeta.auth.config;

import cn.nobeta.auth.module.account.AccountService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapConfig {
    @Bean ApplicationRunner initializeAdministrator(AuthProperties properties, AccountService accounts) {
        return arguments -> {
            var bootstrap = properties.bootstrap();
            if (bootstrap.enabled()) {
                if (bootstrap.username() == null || bootstrap.username().isBlank()
                        || bootstrap.password() == null || bootstrap.password().isBlank()) {
                    throw new IllegalStateException("Explicit bootstrap administrator credentials are required");
                }
                accounts.bootstrapAdministrator(bootstrap.username(), bootstrap.password());
            }
        };
    }
}
