package com.auth_service.security.oauth2;

import com.auth_service.entity.*;
import com.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Loads or creates a local User record from the OAuth2 provider data.
 * Called by Spring Security after a successful OAuth2 code exchange.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = resolveUserInfo(registrationId, oAuth2User.getAttributes());

        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OAuth2 provider");
        }

        User user = userRepository.findByEmail(userInfo.getEmail())
                .map(existing -> updateExistingUser(existing, userInfo))
                .orElseGet(() -> registerNewUser(registrationId, userInfo));

        Map<String, Object> attrs = new HashMap<>(oAuth2User.getAttributes());
        attrs.put("userId", user.getUserId());

        return new DefaultOAuth2User(
                Collections.emptyList(),
                attrs,
                request.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName()
        );
    }

    private OAuth2UserInfo resolveUserInfo(String registrationId, Map<String, Object> attrs) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attrs);
            default -> throw new OAuth2AuthenticationException(
                    "Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private User registerNewUser(String registrationId, OAuth2UserInfo info) {
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
        String username = generateUsername(info.getEmail());

        User user = User.builder()
                .email(info.getEmail())
                .fullName(info.getName() != null ? info.getName() : username)
                .username(username)
                .avatarUrl(info.getImageUrl())
                .provider(provider)
                .providerId(info.getId())
                .status(UserStatus.ONLINE)
                .isActive(true)
                .emailVerified(true)   // Google/OAuth2 already verifies the email
                .build();

        log.info("Registering new OAuth2 user: {} via {}", info.getEmail(), registrationId);
        return userRepository.save(user);
    }

    private User updateExistingUser(User user, OAuth2UserInfo info) {
        if (info.getName() != null) {
            user.setFullName(info.getName());
        }
        // Always sync latest profile photo from the OAuth2 provider
        if (info.getImageUrl() != null) {
            user.setAvatarUrl(info.getImageUrl());
        }
        return userRepository.save(user);
    }

    private String generateUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}
