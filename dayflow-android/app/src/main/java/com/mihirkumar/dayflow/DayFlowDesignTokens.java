package com.mihirkumar.dayflow;

/**
 * Semantic color tokens for the light DayFlow UI. Keep the vocabulary small
 * and change roles intentionally instead of editing legacy hex aliases globally.
 */
final class DayFlowDesignTokens {
    static final String CANVAS = "#F4F6F1";
    static final String SURFACE = "#FFFFFF";
    static final String SURFACE_ALT = "#EAF0E9";
    static final String TEXT_PRIMARY = "#18372E";
    static final String TEXT_SECONDARY = "#4A6053";
    static final String ACCENT = "#286B55";
    static final String SUCCESS_SURFACE = "#DCEBE2";
    static final String WARNING_TEXT = "#7A4A0F";
    static final String WARNING_SURFACE = "#F6EEDB";
    static final String DANGER_TEXT = "#A12F36";
    static final String DANGER_SURFACE = "#F8E8E6";
    static final String BORDER = "#DDE6DE";

    private DayFlowDesignTokens() {}

    /** WCAG relative-luminance contrast ratio for six-digit RGB hex colors. */
    static double contrastRatio(String foregroundHex, String backgroundHex) {
        double foreground = luminance(rgb(foregroundHex));
        double background = luminance(rgb(backgroundHex));
        return (Math.max(foreground, background) + 0.05)
                / (Math.min(foreground, background) + 0.05);
    }

    private static int[] rgb(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Color must be a six-digit RGB hex value.");
        }
        return new int[] {
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    private static double luminance(int[] rgb) {
        double red = linearChannel(rgb[0] / 255.0);
        double green = linearChannel(rgb[1] / 255.0);
        double blue = linearChannel(rgb[2] / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
