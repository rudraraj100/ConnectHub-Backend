package com.auth_service.entity;

/**
 * Subscription plan for a user.
 *
 *  FREE    — default for every new registration
 *  PREMIUM — unlocked via payment; grants:
 *             • Create group rooms
 *             • Unlimited message history (free = 30 days)
 *             • Custom status text
 *             • Premium avatar badge
 *             • Pin messages in rooms
 */
public enum PlanType {
    FREE,
    PREMIUM
}
