package cn.litx.serverpulse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PulseSettingsTest {

    @Test
    void defaultsAreLoginOnly() {
        var settings = PulseSettings.defaults();

        assertEquals("login", settings.accessMode());
        assertEquals("auto", settings.theme());
        assertEquals(2, settings.refreshInterval());
        assertFalse(settings.publicAccess());
        assertFalse(settings.specificUsersAccess());
        assertTrue(settings.allowedUsers().isEmpty());
        assertTrue(settings.showHistory());
        assertTrue(settings.showDetails());
    }

    @Test
    void invalidValuesAreNormalized() {
        var settings = new PulseSettings(
            "  my server  ",
            "PURPLE",
            0,
            "everyone",
            null,
            null,
            null
        ).normalized();

        assertEquals("my server", settings.serverName());
        assertEquals("auto", settings.theme());
        assertEquals(1, settings.refreshInterval());
        assertEquals("login", settings.accessMode());
        assertTrue(settings.showHistory());
        assertTrue(settings.showDetails());
        assertTrue(settings.allowedUsers().isEmpty());
    }

    @Test
    void refreshIntervalIsClamped() {
        assertEquals(30, new PulseSettings("", "auto", 99, "login", true, true, List.of())
            .normalized().refreshInterval());
        assertEquals(1, new PulseSettings("", "auto", -2, "login", true, true, List.of())
            .normalized().refreshInterval());
    }

    @Test
    void publicModeIsExplicit() {
        var settings = new PulseSettings("", "dark", 5, "public", true, false, List.of()).normalized();

        assertTrue(settings.publicAccess());
        assertFalse(settings.specificUsersAccess());
        assertEquals("dark", settings.theme());
        assertEquals(5, settings.refreshInterval());
        assertFalse(settings.showDetailsEnabled());
    }

    @Test
    void specificUsersModeIsExplicit() {
        var settings = new PulseSettings("", "auto", 3, "USERS", true, true, List.of("alice")).normalized();

        assertEquals("users", settings.accessMode());
        assertTrue(settings.specificUsersAccess());
        assertFalse(settings.publicAccess());
        assertEquals(List.of("alice"), settings.allowedUsers());
    }

    @Test
    void allowedUsersAreTrimmedAndDeduplicated() {
        var settings = new PulseSettings(
            "", "auto", 3, "users", true, true,
            Arrays.asList(" alice ", "bob", "alice", "", null)
        ).normalized();

        assertEquals(List.of("alice", "bob"), settings.allowedUsers());
    }
}
