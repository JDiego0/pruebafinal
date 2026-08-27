package co.riwi.messaging.infrastructure.web;

import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.infrastructure.security.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * AUTH-09: the ONLY place an {@link AuthenticatedActor} is constructed, and
 * it is built exclusively from a verified JWT's claims — never from a
 * request body, path or query parameter. Public routes (login, refresh,
 * health, docs) are skipped; every other route that reaches a controller
 * without a valid token gets 401 from {@link ActorArgumentResolver}, which
 * finds no actor attribute set.
 */
@Component
@Order(2)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACTOR_ATTRIBUTE = "authenticatedActor";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/health",
            "/api/v1/openapi.json", "/swagger-ui", "/v3/api-docs"
    );

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            try {
                JwtService.VerifiedAccessToken verified = jwtService.verify(token);
                request.setAttribute(ACTOR_ATTRIBUTE, new AuthenticatedActor(
                        verified.userId(), verified.role(), verified.displayName(), verified.locale()
                ));
            } catch (JwtException | IllegalArgumentException ignored) {
                // No actor attribute set — ActorArgumentResolver / advice turns this into 401.
            }
        }
        chain.doFilter(request, response);
    }
}
