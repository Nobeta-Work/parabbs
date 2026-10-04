package cn.nobeta.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refresh cookie-session authorities and account status, leaving bearer/client authentication alone. */
public final class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountDetailsService details;
    public AccountSessionFilter(AccountDetailsService details) { this.details = details; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UsernamePasswordAuthenticationToken && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails) {
            try {
                UserDetails current = details.loadBySubject(authentication.getName());
                if (!current.isEnabled()) throw new UsernameNotFoundException("Inactive account");
                var refreshed = UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities());
                refreshed.setDetails(authentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(refreshed);
            } catch (UsernameNotFoundException exception) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
