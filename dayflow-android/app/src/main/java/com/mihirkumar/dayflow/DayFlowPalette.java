package com.mihirkumar.dayflow;

import java.util.Locale;

/**
 * Maps legacy dark-theme hex values to the current light-theme palette.
 * Kept pure and unit-tested so view code can migrate to explicit design tokens safely.
 */
final class DayFlowPalette {
    private DayFlowPalette() {}

    static String textHex(String hex) {
        String key = hex == null ? "" : hex.toUpperCase(Locale.US);
        switch (key) {
            case "#0D1512": case "#0B0D12": case "#0F131C": case "#111A16":
                return "#F4F6F1";
            case "#131D18": case "#152019": case "#1B2621":
                return "#EAF0E9";
            case "#17231D": case "#1B2922": case "#18231E":
                return "#FFFFFF";
            case "#23352C": case "#283A30": case "#263243": case "#202936":
                return "#496257";
            case "#23845F": case "#40B58A": case "#27513E": case "#245B45":
            case "#243F34": case "#2C8061": case "#3F8E69":
                return "#286B55";
            case "#70D6A5": case "#6BD0A6": case "#7DD6B2": case "#6FD6A3":
            case "#65C5A0":
                return "#2E8060";
            case "#203E30": case "#172B22": case "#DCEBE3":
                return "#DCEBE2";
            case "#3A2729": case "#F8E8E6": case "#B84B5C":
            case "#B44248": case "#FFC5C9":
                return "#B44248";
            case "#D5B56E": return "#A76821";
            case "#F2F7F3": case "#F1F6F2": case "#F5F6F1":
                return "#18372E";
            case "#F5FBF7":
                return Color.WHITE;
            case "#D6E4DA": case "#D6E1D8": case "#DCE2EE": case "#DCE7DF":
                return "#496257";
            case "#9AABA0": case "#91A197": case "#82948A": case "#A5B5AA":
            case "#A8B0C2": case "#8A9A90": case "#8E98AA": case "#AEB7C9":
            case "#A8BAAE": case "#DEE8E0": case "#7F9689": case "#47584D":
            case "#B7C7BC":
                return "#687A70";
            case "#FFFFFF": return Color.WHITE;
            default: return hex;
        }
    }

    static String backgroundHex(String hex) {
        String key = hex == null ? "" : hex.toUpperCase(Locale.US);
        switch (key) {
            case "#0D1512": case "#0B0D12": case "#0F131C": case "#111A16":
                return "#F4F6F1";
            case "#131D18": case "#152019": case "#1B2621": case "#263A31":
            case "#34483D": case "#47584D":
                return "#EAF0E9";
            case "#17231D": case "#1B2922": case "#18231E":
                return Color.WHITE;
            case "#23352C": case "#283A30": case "#263243": case "#202936":
            case "#214036":
                return Color.WHITE;
            case "#23845F": case "#40B58A": case "#27513E": case "#245B45":
            case "#243F34": case "#2C8061": case "#3F8E69": case "#286B55":
            case "#2E8060":
                return "#286B55";
            case "#B84B5C": case "#B44248":
                return "#B44248";
            case "#70D6A5": case "#6BD0A6": case "#7DD6B2": case "#6FD6A3":
            case "#65C5A0": case "#203E30": case "#172B22": case "#DCEBE3":
            case "#D2F1E2": case "#DCEBE2":
                return "#DCEBE2";
            case "#3A2729": case "#F8E8E6":
                return "#F8E8E6";
            case "#D5B56E": return "#F6EEDB";
            case "#D6E4DA": case "#D6E1D8": case "#DCE2EE": case "#DCE7DF":
            case "#E6EEE7": case "#EAF0E9":
                return "#EAF0E9";
            case "#F2F7F3": case "#F1F6F2": case "#F5FBF7": case "#F5F6F1":
            case "#FFFFFF":
                return Color.WHITE;
            default: return hex;
        }
    }
}
