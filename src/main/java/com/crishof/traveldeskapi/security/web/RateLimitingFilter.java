package com.crishof.traveldeskapi.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting basico, en memoria y sin dependencias externas, para los endpoints
 * de autenticacion mas sensibles (login, refresh, recuperacion de contrasena).
 *
 * <p>Ventana fija por (IP cliente + path). Al superar el limite responde 429 con cabecera
 * {@code Retry-After}. Pensado para una unica instancia (despliegue actual en Railway);
 * si en el futuro se escala horizontalmente conviene mover el contador a un store compartido
 * (p. ej. Redis).</p>
 *
 * <p>Configurable via propiedades:
 * {@code app.security.rate-limit.enabled} (default true),
 * {@code app.security.rate-limit.max-requests} (default 10),
 * {@code app.security.rate-limit.window-seconds} (default 60).</p>
 */
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/resend-verification");

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final SecurityErrorResponseWriter errorWriter;
    private final boolean enabled;
    private final int maxRequests;
    private final long windowMillis;

    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            SecurityErrorResponseWriter errorWriter,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.max-requests:10}") int maxRequests,
            @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
        this.errorWriter = errorWriter;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final long now = System.currentTimeMillis();
        final String key = clientIp(request) + "|" + request.getRequestURI();

        final Window window = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });

        if (window.count > maxRequests) {
            final long retryAfterSeconds = Math.max(1L, (windowMillis - (now - window.startMillis)) / 1000L);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            log.warn("Rate limit superado para {} en {}", clientIp(request), request.getRequestURI());
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    "Demasiadas peticiones. Intentalo de nuevo en " + retryAfterSeconds + " segundos.");
            return;
        }

        evictExpiredIfNeeded(now);
        filterChain.doFilter(request, response);
    }

    private void evictExpiredIfNeeded(long now) {
        if (counters.size() > MAX_TRACKED_KEYS) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().startMillis >= windowMillis);
        }
    }

    private String clientIp(HttpServletRequest request) {
        final @Nullable String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Contador de ventana fija. Se muta unicamente dentro del bloque atomico de {@link Map#compute}. */
    private static final class Window {
        private final long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
            this.count = 1;
        }
    }
}
