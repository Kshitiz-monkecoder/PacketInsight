package com.dpi.types;

/**
 * Application Classification Types
 * Maps domain names (SNI) to application categories
 */
public enum AppType {
    UNKNOWN,
    HTTP,
    HTTPS,
    DNS,
    TLS,
    QUIC,

    // Specific applications (detected via SNI)
    GOOGLE,
    FACEBOOK,
    YOUTUBE,
    TWITTER,
    INSTAGRAM,
    NETFLIX,
    AMAZON,
    MICROSOFT,
    APPLE,
    WHATSAPP,
    TELEGRAM,
    TIKTOK,
    SPOTIFY,
    ZOOM,
    DISCORD,
    GITHUB,
    CLOUDFLARE;

    /**
     * Classify application based on SNI (Server Name Indication)
     */
    public static AppType fromSNI(String sni) {
        if (sni == null || sni.isEmpty()) {
            return UNKNOWN;
        }

        String lower = sni.toLowerCase();

        // Check for specific applications
        if (lower.contains("youtube")) return YOUTUBE;
        if (lower.contains("facebook") || lower.contains("fbcdn")) return FACEBOOK;
        if (lower.contains("google") || lower.contains("googleapis")) return GOOGLE;
        if (lower.contains("twitter") || lower.contains("twimg")) return TWITTER;
        if (lower.contains("instagram") || lower.contains("cdninstagram")) return INSTAGRAM;
        if (lower.contains("netflix") || lower.contains("nflxvideo")) return NETFLIX;
        if (lower.contains("amazon") || lower.contains("amazonaws")) return AMAZON;
        if (lower.contains("microsoft") || lower.contains("live.com") || lower.contains("office")) return MICROSOFT;
        if (lower.contains("apple") || lower.contains("icloud")) return APPLE;
        if (lower.contains("whatsapp")) return WHATSAPP;
        if (lower.contains("telegram")) return TELEGRAM;
        if (lower.contains("tiktok") || lower.contains("musically")) return TIKTOK;
        if (lower.contains("spotify") || lower.contains("scdn.co")) return SPOTIFY;
        if (lower.contains("zoom")) return ZOOM;
        if (lower.contains("discord") || lower.contains("discordapp")) return DISCORD;
        if (lower.contains("github") || lower.contains("githubusercontent")) return GITHUB;
        if (lower.contains("cloudflare")) return CLOUDFLARE;

        return HTTPS;  // Generic HTTPS traffic
    }

    /**
     * Get display name for reporting
     */
    public String getDisplayName() {
        return switch (this) {
            case UNKNOWN -> "Unknown";
            case HTTP -> "HTTP";
            case HTTPS -> "HTTPS";
            case DNS -> "DNS";
            case TLS -> "TLS";
            case QUIC -> "QUIC";
            case GOOGLE -> "Google";
            case FACEBOOK -> "Facebook";
            case YOUTUBE -> "YouTube";
            case TWITTER -> "Twitter";
            case INSTAGRAM -> "Instagram";
            case NETFLIX -> "Netflix";
            case AMAZON -> "Amazon";
            case MICROSOFT -> "Microsoft";
            case APPLE -> "Apple";
            case WHATSAPP -> "WhatsApp";
            case TELEGRAM -> "Telegram";
            case TIKTOK -> "TikTok";
            case SPOTIFY -> "Spotify";
            case ZOOM -> "Zoom";
            case DISCORD -> "Discord";
            case GITHUB -> "GitHub";
            case CLOUDFLARE -> "Cloudflare";
        };
    }
}
