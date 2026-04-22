package com.api_gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * GatewaySecurityConfig — §6 API Gateway Security.
 *
 * The gateway is a PURE PROXY — it does NOT authenticate requests itself.
 * Authentication is handled by auth-service downstream.
 *
 * This config:
 *   1. Permits ALL inbound requests (no gateway-level auth)
 *   2. Disables CSRF (REST API, stateless)
 *   3. Disables form login (removes the built-in /login redirect)
 *   4. Disables HTTP Basic authentication
 *   5. Configures CORS so Angular (port 4201) can call the gateway
 */
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Allow every request through — auth is done by auth-service ──
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

            // ── Stateless — no HTTP session at the gateway ──
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Disable CSRF — REST proxy, no form submissions ──
            .csrf(AbstractHttpConfigurer::disable)

            // ── Disable form login — removes the built-in /login redirect ──
            .formLogin(AbstractHttpConfigurer::disable)

            // ── Disable HTTP Basic — no pop-up auth dialogs ──
            .httpBasic(AbstractHttpConfigurer::disable)

            // ── Apply CORS config ──
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    /**
     * CORS for Angular dev server (port 4201).
     * Allows credentials so JWT Bearer tokens in headers are forwarded.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Use pattern matching so Spring reflects the exact request origin back
        // (avoids duplicate header if auth-service also adds CORS).
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
