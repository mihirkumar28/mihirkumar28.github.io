package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class DayFlowPaletteTest {
    @Test public void mapsLegacyDarkTextAndSurfaceColorsToLightTheme() {
        assertEquals("#F4F6F1", DayFlowPalette.textHex("#0D1512"));
        assertEquals("#EAF0E9", DayFlowPalette.textHex("#152019"));
        assertEquals("#FFFFFF", DayFlowPalette.backgroundHex("#18231E"));
    }

    @Test public void mapsLegacyLightTextAndAccentColorsToReadablePalette() {
        assertEquals("#18372E", DayFlowPalette.textHex("#F2F7F3"));
        assertEquals("#286B55", DayFlowPalette.textHex("#40B58A"));
        assertEquals("#2E8060", DayFlowPalette.textHex("#7DD6B2"));
    }

    @Test public void mapsSurfaceVariantsWithoutChangingTheirMeaning() {
        assertEquals("#DCEBE2", DayFlowPalette.backgroundHex("#DCEBE3"));
        assertEquals("#F8E8E6", DayFlowPalette.backgroundHex("#F8E8E6"));
        assertEquals("#FFFFFF", DayFlowPalette.backgroundHex("#FFFFFF"));
        assertEquals("#496257", DayFlowPalette.textHex("#23352C"));
    }

    @Test public void preservesUnknownHexValuesAndIsCaseInsensitiveForAliases() {
        assertEquals("#F4F6F1", DayFlowPalette.textHex("#0d1512"));
        assertEquals("#12AB34", DayFlowPalette.backgroundHex("#12AB34"));
        assertEquals("#12AB34", DayFlowPalette.textHex("#12AB34"));
    }
}
