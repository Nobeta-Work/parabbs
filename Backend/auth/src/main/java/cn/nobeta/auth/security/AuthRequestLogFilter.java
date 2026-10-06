package cn.nobeta.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Covers framework endpoints as well as controllers, without logging credentials or query strings. */
public final class AuthRequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRequestLogFilter.class);

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get("authRequestId");
        String requestId = UUID.randomUUID().toString();
        MDC.put("authRequestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
        boolean cookiePresent = request.getCookies() != null && Arrays.stream(request.getCookies())
                .anyMatch(cookie -> "AUTH_SESSION".equals(cookie.getName()));
        long started = System.nanoTime();
        log.info("Auth request method={} path={} contentType={} secure={} sessionCookiePresent={} sessionPresent={}",
                request.getMethod(), path, request.getContentType(), request.isSecure(), cookiePresent,
                request.getSession(false) != null);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            // Exception messages can contain SQL parameters or credentials; record only the type.
            log.error("Auth request failed method={} path={} exceptionType={}",
                    request.getMethod(), path, exception.getClass().getSimpleName());
            throw exception;
        } finally {
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            String redirectPath = "none";
            String location = response.getHeader("Location");
            if (location != null) {
                try {
                    redirectPath = java.net.URI.create(location).getPath().replaceAll("[\\r\\n\\t]", "_");
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    redirectPath = "unparseable";
                }
            }
            // A callback Location can contain a code: record its path only.
            log.info("Auth response method={} path={} status={} redirectPath={} durationMs={}",
                    request.getMethod(), path, response.getStatus(), redirectPath, elapsed);
            if (previous == null) MDC.remove("authRequestId");
            else MDC.put("authRequestId", previous);
        }
    }
}
