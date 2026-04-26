package com.api_gateway;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * JwtGatewayFilter — the PRIMARY security gate for the entire platform.
 *
 * Responsibilities:
 *  1. Let public paths through without a token (login, register, oauth2, etc.)
 *  2. Strip any incoming X-User-Id / X-User-Email headers (prevents client spoofing)
 *  3. Validate the Bearer token (signature + expiry) using JJWT
 *  4. Inject X-User-Id and X-User-Email into the forwarded request
 *  5. Return 401 JSON for missing or invalid tokens on protected paths
 *
 * Auth-service keeps its own Spring Security filter chain for:
 *  - The OAuth2 dance (state parameter requires session, not JWT)
 *  - A second line of defence for its own endpoints
 *
 * Room Service reads X-User-Id from the header injected here — it does
 * NOT need to parse the JWT itself.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // After Spring's built-in CORS handling (globalcors in YAML)
public class JwtGatewayFilter extends OncePerRequestFilter {

    /** Headers the gateway injects — clients must NOT be allowed to forge these. */
    private static final Set<String> STRIPPED_HEADERS = Set.of(
            "x-user-id", "x-user-email"
    );

    /**
     * Paths that don't require a JWT.
     * OAuth2 paths go directly to auth-service which handles them via Spring Security.
     */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/check-username",
            "/oauth2/",
            "/login/oauth2/",
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ── Main filter logic ────────────────────────────────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");

        // ── Set CORS headers immediately — must be BEFORE chain.doFilter() ──
        // Once the response body starts streaming, headers are already committed
        // and cannot be added. No @CrossOrigin on any downstream service, so
        // there is no risk of duplicate Access-Control-Allow-Origin headers.
        setCorsHeaders(response, origin);

        // ── Pre-flight OPTIONS: respond immediately, no auth needed ──────────
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = request.getRequestURI();

        // Public paths — skip JWT validation
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract Bearer token
        String token = resolveToken(request);
        if (token == null) {
            reject(response, "Missing or invalid Authorization header");
            return;
        }

        // Validate and extract claims
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("[Gateway] Token expired for path {}: {}", path, e.getMessage());
            reject(response, "Token has expired");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[Gateway] Invalid token for path {}: {}", path, e.getMessage());
            reject(response, "Invalid token");
            return;
        }

        String userId = claims.get("userId", String.class);
        String email  = claims.getSubject();
        log.debug("[Gateway] Authenticated userId={} email={} → {}", userId, email, path);

        HttpServletRequest enriched = new HeaderMutatingRequest(request, userId, email);
        chain.doFilter(enriched, response);
    }

    /** Sets full CORS header suite on every response. */
    private void setCorsHeaders(HttpServletResponse response, String origin) {
        if (origin == null) return;
        response.setHeader("Access-Control-Allow-Origin",      origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods",     "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",     "Authorization,Content-Type,X-User-Id,X-Requested-With");
        response.setHeader("Access-Control-Expose-Headers",    "Authorization,X-User-Id");
        response.setHeader("Access-Control-Max-Age",           "3600");
        response.setHeader("Vary",                             "Origin");
    }


    // ── Helpers ──────────────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
        );
    }

    // ── Inner class: mutates headers without servlet-impl hacks ─────

    /**
     * HttpServletRequestWrapper that:
     *  - Strips X-User-Id and X-User-Email from the original request (anti-spoofing)
     *  - Returns gateway-validated values for those headers
     */
    private static class HeaderMutatingRequest extends HttpServletRequestWrapper {

        private final String userId;
        private final String email;

        HeaderMutatingRequest(HttpServletRequest request, String userId, String email) {
            super(request);
            this.userId = userId;
            this.email  = email;
        }

        @Override
        public String getHeader(String name) {
            if (STRIPPED_HEADERS.contains(name.toLowerCase())) {
                return getInjected(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (STRIPPED_HEADERS.contains(name.toLowerCase())) {
                String val = getInjected(name);
                return val != null ? Collections.enumeration(List.of(val))
                                   : Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            // Ensure our injected headers are always present
            if (!names.contains("X-User-Id"))    names.add("X-User-Id");
            if (!names.contains("X-User-Email")) names.add("X-User-Email");
            return Collections.enumeration(names);
        }

        private String getInjected(String name) {
            return "x-user-id".equalsIgnoreCase(name)    ? userId
                 : "x-user-email".equalsIgnoreCase(name) ? email
                 : null;
        }
    }
}
