package com.auth_service.entity;

/**
 * Platform-level role for a user account.
 *
 * MEMBER        — default for every new sign-up.
 * PLATFORM_ADMIN — grants access to the platform admin dashboard.
 *
 * This role is intentionally NOT settable via any REST endpoint.
 * To promote a user, run the following SQL directly on the DB:
 *
 *   UPDATE users SET role = 'PLATFORM_ADMIN' WHERE email = 'you@example.com';
 *
 * To demote back to member:
 *
 *   UPDATE users SET role = 'MEMBER' WHERE email = 'you@example.com';
 */
public enum PlatformRole {
    MEMBER,
    PLATFORM_ADMIN
}
