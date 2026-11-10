package cn.litx.serverpulse.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * User-configurable ServerPulse settings.
 *
 * <p>Values are normalized on the server. Missing or invalid values fall back to the
 * plugin default: login-only access, automatic theme and a 2 second refresh.</p>
 */
public record PulseSettings(
    String serverName,
    String theme,
    Integer refreshInterval,
    String accessMode,
    Boolean showHistory,
    Boolean showDetails,
    List<String> allowedUsers
) {

    public static final String GROUP = "general";
    public static final String THEME_AUTO = "auto";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String ACCESS_LOGIN = "login";
    public static final String ACCESS_PUBLIC = "public";
    public static final String ACCESS_USERS = "users";
    public static final int MIN_REFRESH_SECONDS = 1;
    public static final int MAX_REFRESH_SECONDS = 30;
    public static final int DEFAULT_REFRESH_SECONDS = 2;

    public static PulseSettings defaults() {
        return new PulseSettings(
            "", THEME_AUTO, DEFAULT_REFRESH_SECONDS, ACCESS_LOGIN, true, true, List.of());
    }

    /**
     * Returns a safe copy of the settings. This method is idempotent.
     */
    public PulseSettings normalized() {
        var normalizedServerName = serverName == null ? "" : serverName.trim();
        var normalizedTheme = normalizeTheme(theme);
        var normalizedAccess = normalizeAccessMode(accessMode);
        var normalizedRefresh = normalizeRefresh(refreshInterval);
        return new PulseSettings(
            normalizedServerName,
            normalizedTheme,
            normalizedRefresh,
            normalizedAccess,
            showHistory == null || showHistory,
            showDetails == null || showDetails,
            normalizeUsers(allowedUsers)
        );
    }

    public boolean publicAccess() {
        return ACCESS_PUBLIC.equals(normalized().accessMode());
    }

    public boolean specificUsersAccess() {
        return ACCESS_USERS.equals(normalized().accessMode());
    }

    public boolean showHistoryEnabled() {
        return Boolean.TRUE.equals(normalized().showHistory());
    }

    public boolean showDetailsEnabled() {
        return Boolean.TRUE.equals(normalized().showDetails());
    }

    public String effectiveTheme() {
        return normalized().theme();
    }

    public int refreshSeconds() {
        return normalized().refreshInterval();
    }

    private static String normalizeTheme(String value) {
        if (value == null) {
            return THEME_AUTO;
        }
        var candidate = value.trim().toLowerCase(Locale.ROOT);
        return switch (candidate) {
            case THEME_LIGHT, THEME_DARK, THEME_AUTO -> candidate;
            default -> THEME_AUTO;
        };
    }

    private static String normalizeAccessMode(String value) {
        if (value == null) {
            return ACCESS_LOGIN;
        }
        var candidate = value.trim().toLowerCase(Locale.ROOT);
        return switch (candidate) {
            case ACCESS_PUBLIC, ACCESS_USERS -> candidate;
            default -> ACCESS_LOGIN;
        };
    }

    private static List<String> normalizeUsers(List<String> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        var users = new LinkedHashSet<String>();
        for (var entry : value) {
            if (entry == null) {
                continue;
            }
            var username = entry.trim();
            if (!username.isEmpty()) {
                users.add(username);
            }
        }
        return List.copyOf(users);
    }

    private static int normalizeRefresh(Integer value) {
        if (value == null) {
            return DEFAULT_REFRESH_SECONDS;
        }
        return Math.max(MIN_REFRESH_SECONDS, Math.min(MAX_REFRESH_SECONDS, value));
    }
}
