package vn.unicom.thi360.shared.appearance.domain;

import java.util.Set;
import java.util.regex.Pattern;

public final class AppearancePolicy {

    public static final String SETTING_KEY = "appearance";
    public static final String DEFAULT_PRESET_ID = "thi360-green";
    public static final String DEFAULT_MODE = "light";
    public static final String CUSTOM_PRESET_ID = "custom";

    public static final Set<String> PRESET_IDS = Set.of(
            "thi360-green",
            "emerald-gold",
            "charcoal-teal",
            "navy-teal",
            "blue-silver",
            "sky-orange",
            "indigo-amber",
            "indigo-coral",
            "slate-crimson"
    );

    public static final Set<String> MODES = Set.of("light", "dark");

    public static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private AppearancePolicy() {
    }

    public static boolean isKnownPreset(String presetId) {
        return presetId != null && PRESET_IDS.contains(presetId);
    }

    public static boolean isCustomPreset(String presetId) {
        return CUSTOM_PRESET_ID.equals(presetId);
    }

    public static boolean isValidPresetOrCustom(String presetId) {
        return isKnownPreset(presetId) || isCustomPreset(presetId);
    }

    public static boolean isValidMode(String mode) {
        return mode != null && MODES.contains(mode);
    }

    public static boolean isValidHexColor(String hex) {
        return hex != null && HEX_COLOR_PATTERN.matcher(hex).matches();
    }
}
