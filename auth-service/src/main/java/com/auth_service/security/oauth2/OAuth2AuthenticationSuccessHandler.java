package com.auth_service.security.oauth2;

import com.auth_service.entity.User;
import com.auth_service.repository.UserRepository;
import com.auth_service.security.JwtUtil;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * After a successful OAuth2 login, generates a JWT and redirects
 * the browser to the Angular frontend with the token as a query param.
 * Frontend route: /oauth2/callback?token=JWT&userId=UUID
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil        jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String userId = (String) oAuth2User.getAttributes().get("userId");

        User user = userRepository.findById(userId).orElseThrow();

        String token        = jwtUtil.generateToken(user.getUserId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail());

        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth2/callback")
                .queryParam("token", token)
                .queryParam("refreshToken", refreshToken)
                .queryParam("userId", userId)
                .build().toUriString();

        log.info("OAuth2 login success for user: {} — redirecting to frontend", user.getEmail());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
