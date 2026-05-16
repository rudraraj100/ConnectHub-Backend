package com.api_gateway;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JwtGatewayFilter is the "Security Guard" of the microservices.
 * It sits at the edge and checks every incoming request to ensure the user is authenticated.
 * It also protects the internal services from "header spoofing" by stripping user headers from the client.
 */
@Slf4j
@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> STRIPPED_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Plan"
    );

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/check-username",
            "/auth/verify-otp",           // OTP verification — no JWT yet
            "/auth/resend-verification",    // resend OTP email — no JWT yet
            "/oauth2/",
            "/login/oauth2/",
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/webjars/",                   // Swagger UI static assets (JS, CSS, fonts)
            "/media/view/"
    );

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /** Reactive Redis — shared instance with auth-service to read suspended: keys. */
    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Strip spoofable identity headers unconditionally
        ServerHttpRequest stripped = request.mutate()
                .headers(h -> STRIPPED_HEADERS.forEach(h::remove))
                .build();
        exchange = exchange.mutate().request(stripped).build();

        // CORS preflight — pass through before any auth check
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // Public paths (like login/register) don't need a token
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String token = resolveToken(exchange.getRequest());
        if (token == null) {
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        // Validate JWT signature + expiry
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("[Gateway] Token expired for path {}: {}", path, e.getMessage());
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[Gateway] Invalid token for path {}: {}", path, e.getMessage());
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String userId = claims.get("userId", String.class);
        String email  = claims.getSubject();
        String plan   = claims.get("plan", String.class);
        log.debug("[Gateway] Authenticated userId={} plan={} path={}", userId, plan, path);

        // ── Suspension check (Redis reactive) ────────────────────────────────
        // auth-service writes "suspended:{userId}" to Redis when an admin suspends a user.
        // Checking here ensures suspended users are blocked at the edge, even with valid JWTs.
        final ServerWebExchange finalExchange = exchange;

        if (userId != null) {
            return redisTemplate.hasKey("suspended:" + userId)
                    .flatMap(suspended -> {
                        if (Boolean.TRUE.equals(suspended)) {
                            log.warn("[Gateway] Rejected suspended user {} on {}", userId, path);
                            return reject(finalExchange.getResponse(), HttpStatus.FORBIDDEN,
                                    "Your account has been suspended. Please contact support.");
                        }
                        return forward(finalExchange, chain, userId, email, plan);
                    });
        }

        // If the user is NOT suspended, we "enrich" the request with their ID and continue
        return forward(exchange, chain, userId, email, plan);
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
                                String userId, String email, String plan) {
        ServerWebExchange enriched = exchange.mutate()
                .request(r -> r.headers(h -> {
                    if (userId != null) h.set("X-User-Id",    userId);
                    if (email  != null) h.set("X-User-Email", email);
                    h.set("X-User-Plan", plan != null ? plan : "FREE");
                }))
                .build();
        return chain.filter(enriched);
    }

    private boolean isPublicPath(String path) {
        if ("/ws".equals(path) || "/ws-notifications".equals(path)) return true;
        // Allow all Swagger/OpenAPI doc endpoints (e.g. /auth/v3/api-docs, /rooms/v3/api-docs)
        if (path.contains("v3/api-docs") || path.contains("swagger-ui") || path.contains("webjars")) return true;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) return null;
        String bearer = authHeaders.get(0);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value() + ",\"error\":\"" +
                status.getReasonPhrase() + "\",\"message\":\"" + message + "\"}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
