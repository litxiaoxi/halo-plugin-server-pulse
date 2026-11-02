package cn.litx.serverpulse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseSettingsTest {

    @Test
    void defaultsAreLoginOnly() {
        var settings = PulseSettings.defaults();

        assertEquals("login", settings.accessMode());
        assertEquals("auto", settings.theme());
        assertEquals(3, settings.refreshInterval());
        assertFalse(settings.publicAccess());
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
            null
        ).normalized();

        assertEquals("my server", settings.serverName());
        assertEquals("auto", settings.theme());
        assertEquals(1, settings.refreshInterval());
        assertEquals("login", settings.accessMode());
        assertTrue(settings.showHistory());
        assertTrue(settings.showDetails());
    }

    @Test
    void refreshIntervalIsClamped() {
        assertEquals(30, new PulseSettings("", "auto", 99, "login", true, true)
            .normalized().refreshInterval());
        assertEquals(1, new PulseSettings("", "auto", -2, "login", true, true)
            .normalized().refreshInterval());
    }

    @Test
    void publicModeIsExplicit() {
        var settings = new PulseSettings("", "dark", 5, "public", true, false).normalized();

        assertTrue(settings.publicAccess());
        assertEquals("dark", settings.theme());
        assertEquals(5, settings.refreshInterval());
        assertFalse(settings.showDetailsEnabled());
    }
}
