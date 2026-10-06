package cn.nobeta.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.nobeta.auth.module.account.Account;
import cn.nobeta.auth.module.account.AccountMapper;
import cn.nobeta.auth.module.account.AccountService;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private static final Logger log = LoggerFactory.getLogger(AccountDetailsService.class);
    private final AccountMapper accounts;

    public AccountDetailsService(AccountMapper accounts) { this.accounts = accounts; }

    @Override public UserDetails loadUserByUsername(String username) {
        log.info("Password authentication account lookup");
        Account account = accounts.findByUsername(AccountService.normalizeUsername(username));
        if (account == null) {
            log.warn("Authentication account not found");
            throw new UsernameNotFoundException("Account not found");
        }
        log.info("Authentication account loaded subject={} enabled={}", account.getSubject(), account.getStatus() == 1);
        return details(account, account.getPasswordHash());
    }

    public UserDetails loadBySubject(String subject) {
        Account account = accounts.findBySubject(subject);
        if (account == null) {
            log.warn("Authentication account not found");
            throw new UsernameNotFoundException("Account not found");
        }
        return details(account, "");
    }

    private UserDetails details(Account account, String password) {
        // Framework User is supported by JDBC Security Jackson modules and Redis serialization.
        // Its name is the stable subject, not the mutable login name.
        return User.withUsername(account.getSubject()).password(password)
                .disabled(account.getStatus() != 1)
                .authorities(accounts.roles(account.getId()).toArray(String[]::new)).build();
    }

    @Override @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        Account account = accounts.findBySubject(user.getUsername());
        if (account == null) {
            log.warn("Authentication account not found");
            throw new UsernameNotFoundException("Account not found");
        }
        if (accounts.updatePassword(account.getId(), newPassword, user.getPassword()) != 1) {
            throw new CredentialsExpiredException("Credentials changed during authentication");
        }
        log.info("Password hash upgraded subject={}", user.getUsername());
        Account current = accounts.findBySubject(user.getUsername());
        return details(current, current.getPasswordHash());
    }
}
