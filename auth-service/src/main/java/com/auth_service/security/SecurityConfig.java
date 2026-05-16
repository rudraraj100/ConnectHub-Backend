package com.auth_service.security;

import com.auth_service.security.oauth2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter               jwtAuthFilter;
    private final UserDetailsServiceImpl      userDetailsService;
    private final CustomOAuth2UserService     oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepo;

    // ── Public endpoints — no JWT required ───────────────────────
    private static final String[] PUBLIC_URLS = {
        "/auth/register",
        "/auth/login",
        "/auth/refresh",
        "/auth/forgot-password",
        "/auth/reset-password",
        "/auth/check-username",
        "/auth/verify-otp",             // OTP verification — no JWT yet
        "/auth/resend-verification",    // resend OTP email — no JWT yet
        "/oauth2/**",
        "/login/oauth2/**",
        "/actuator/health",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        // ── Internal service-to-service endpoints ────────────────────────────
        "/auth/internal/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)

            // ── Sessions: IF_REQUIRED so OAuth2 state param survives the redirect ──
            // STATELESS would silently break Google login (state mismatch error).
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // ── Authorization rules ───────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                .anyRequest().authenticated()
            )

            // ── DaoAuthenticationProvider wired explicitly ────────────
            .authenticationProvider(authenticationProvider())

            // ── JWT filter runs before Spring's UsernamePasswordFilter ─
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // ── OAuth2 login (only if Google client is configured) ────────
        if (clientRegistrationRepo.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.userService(oAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
            );
        }

        return http.build();
    }

    /**
     * DaoAuthenticationProvider — used by AuthenticationManager during
     * POST /auth/login to verify email + BCrypt password.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
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
