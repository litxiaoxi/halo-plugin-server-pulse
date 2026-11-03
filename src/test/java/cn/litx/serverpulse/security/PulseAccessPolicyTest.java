package cn.litx.serverpulse.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import cn.litx.serverpulse.config.PulseSettings;

class PulseAccessPolicyTest {

    private final PulseAccessPolicy policy = new PulseAccessPolicy(null);

    @Test
    void loginModeRejectsAnonymousVisitors() {
        assertFalse(policy.allows(PulseSettings.defaults(), ""));
    }

    @Test
    void loginModeAllowsAuthenticatedUsers() {
        assertTrue(policy.allows(PulseSettings.defaults(), "admin"));
    }

    @Test
    void publicModeAllowsAnonymousVisitors() {
        var publicSettings = new PulseSettings("", "auto", 3, "public", true, true, List.of());
        assertTrue(policy.allows(publicSettings, ""));
    }

    @Test
    void unknownAccessModeFailsClosed() {
        var invalid = new PulseSettings("", "auto", 3, "everyone", true, true, List.of());
        assertFalse(policy.allows(invalid, ""));
    }

    @Test
    void specificUsersModeAllowsListedUser() {
        var settings = new PulseSettings("", "auto", 3, "users", true, true, List.of("alice", "bob"));

        assertTrue(policy.allows(settings, "alice"));
        assertTrue(policy.allows(settings, "bob"));
    }

    @Test
    void specificUsersModeRejectsUnlistedUser() {
        var settings = new PulseSettings("", "auto", 3, "users", true, true, List.of("alice"));

        assertFalse(policy.allows(settings, "carol"));
    }

    @Test
    void specificUsersModeRejectsAnonymousVisitors() {
        var settings = new PulseSettings("", "auto", 3, "users", true, true, List.of("alice"));

        assertFalse(policy.allows(settings, ""));
        assertFalse(policy.allows(settings, null));
    }

    @Test
    void specificUsersModeWithEmptyListFailsClosed() {
        var settings = new PulseSettings("", "auto", 3, "users", true, true, null);

        assertFalse(policy.allows(settings, "alice"));
    }

    @Test
    void configuredUsernamesAreTrimmedBeforeMatching() {
        var settings = new PulseSettings("", "auto", 3, "users", true, true, List.of(" alice "));

        assertTrue(policy.allows(settings, "alice"));
    }
}
