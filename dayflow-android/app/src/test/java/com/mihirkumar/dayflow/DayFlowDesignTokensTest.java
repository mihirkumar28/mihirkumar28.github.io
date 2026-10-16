package com.mihirkumar.dayflow;

import org.junit.Test;

import static org.junit.Assert.*;

public class DayFlowDesignTokensTest {
    private static final double NORMAL_TEXT_MINIMUM = 4.5;

    @Test public void secondaryTextMeetsNormalTextContrastOnMainSurfaces() {
        assertContrast(DayFlowDesignTokens.TEXT_SECONDARY, DayFlowDesignTokens.SURFACE);
        assertContrast(DayFlowDesignTokens.TEXT_SECONDARY, DayFlowDesignTokens.CANVAS);
        assertContrast(DayFlowDesignTokens.TEXT_SECONDARY, DayFlowDesignTokens.SURFACE_ALT);
    }

    @Test public void primaryAndAccentTextMeetContrastOnMainSurface() {
        assertContrast(DayFlowDesignTokens.TEXT_PRIMARY, DayFlowDesignTokens.SURFACE);
        assertContrast(DayFlowDesignTokens.TEXT_PRIMARY, DayFlowDesignTokens.CANVAS);
        assertContrast(DayFlowDesignTokens.ACCENT, DayFlowDesignTokens.SURFACE);
    }

    @Test public void statusTextMeetsContrastOnItsSurface() {
        assertContrast(DayFlowDesignTokens.WARNING_TEXT, DayFlowDesignTokens.WARNING_SURFACE);
        assertContrast(DayFlowDesignTokens.DANGER_TEXT, DayFlowDesignTokens.DANGER_SURFACE);
        assertContrast(DayFlowDesignTokens.TEXT_PRIMARY, DayFlowDesignTokens.SUCCESS_SURFACE);
    }

    @Test public void flagsCurrentLowContrastLegacySecondaryColorOnPaleGreenSurface() {
        assertTrue(DayFlowDesignTokens.contrastRatio("#687A70", "#EAF0E9") < NORMAL_TEXT_MINIMUM);
        assertTrue(DayFlowDesignTokens.contrastRatio(
                DayFlowDesignTokens.TEXT_SECONDARY, "#EAF0E9") >= NORMAL_TEXT_MINIMUM);
    }

    @Test public void contrastRatioRejectsNonRgbValues() {
        try {
            DayFlowDesignTokens.contrastRatio("white", "#FFFFFF");
            fail("Non-hex color must be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("six-digit RGB"));
        }
    }

    private void assertContrast(String foreground, String background) {
        assertTrue(foreground + " on " + background + " has low contrast",
                DayFlowDesignTokens.contrastRatio(foreground, background) >= NORMAL_TEXT_MINIMUM);
    }
}
