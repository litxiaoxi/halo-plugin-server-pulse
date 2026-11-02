package cn.litx.serverpulse.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.litx.serverpulse.config.PulseSettings;

class PulseAccessPolicyTest {

    private final PulseAccessPolicy policy = new PulseAccessPolicy(null);

    @Test
    void loginModeRejectsAnonymousVisitors() {
        assertFalse(policy.allows(PulseSettings.defaults(), false));
    }

    @Test
    void loginModeAllowsAuthenticatedUsers() {
        assertTrue(policy.allows(PulseSettings.defaults(), true));
    }

    @Test
    void publicModeAllowsAnonymousVisitors() {
        var publicSettings = new PulseSettings("", "auto", 3, "public", true, true);
        assertTrue(policy.allows(publicSettings, false));
    }

    @Test
    void unknownAccessModeFailsClosed() {
        var invalid = new PulseSettings("", "auto", 3, "everyone", true, true);
        assertFalse(policy.allows(invalid, false));
    }
}
