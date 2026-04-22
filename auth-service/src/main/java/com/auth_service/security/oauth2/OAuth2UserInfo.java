package com.auth_service.security.oauth2;

import java.util.Map;

/** Base class for provider-specific OAuth2 user attribute maps. */
public abstract class OAuth2UserInfo {

    protected final Map<String, Object> attributes;

    protected OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> getAttributes() { return attributes; }

    public abstract String getId();
    public abstract String getName();
    public abstract String getEmail();
    public abstract String getImageUrl();
}
