package com.auth_service.security;

import com.auth_service.security.oauth2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.*;

/**
 * Spring Security configuration.
 * §6 Security: JWT in STOMP CONNECT header, tokens validated on upgrade,
 * TLS, XSS sanitization.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter                     jwtAuthFilter;
    private final UserDetailsServiceImpl            userDetailsService;
    private final CustomOAuth2UserService           oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    /** Public endpoints — no JWT required. */
    private static final String[] PUBLIC_URLS = {
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/check-username",
            "/oauth2/**",
            "/login/oauth2/**",
            "/actuator/health",
            "/actuator/info",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is handled entirely by the API Gateway (port 8080).
                // Disabling it here prevents duplicate Access-Control-Allow-Origin headers.
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(ui -> ui.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
