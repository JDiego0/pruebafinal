package co.riwi.messaging.infrastructure.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * docs/API.md §10. In-memory per-IP token buckets — adequate for a single
 * backend instance (this MVP's target); a multi-instance deployment would
 * need a shared store (Redis), out of scope here.
 */
@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();

        Bucket bucket = null;
        if (path.equals("/api/v1/auth/login")) {
            bucket = loginBuckets.computeIfAbsent(ip, k -> newBucket(10, Duration.ofMinutes(5)));
        } else if (path.equals("/api/v1/auth/refresh")) {
            bucket = refreshBuckets.computeIfAbsent(ip, k -> newBucket(60, Duration.ofMinutes(5)));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429,\"code\":\"RATE_LIMITED\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Bucket newBucket(int capacity, Duration period) {
        return Bucket.builder().addLimit(Bandwidth.simple(capacity, period)).build();
    }
}
