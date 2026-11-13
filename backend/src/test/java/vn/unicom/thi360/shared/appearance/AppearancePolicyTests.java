package vn.unicom.thi360.shared.appearance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.unicom.thi360.shared.appearance.domain.AppearancePolicy;

class AppearancePolicyTests {

    @Test
    void validatesNineKnownPresetsAndCustom() {
        assertThat(AppearancePolicy.PRESET_IDS).containsExactlyInAnyOrder(
                "thi360-green",
                "emerald-gold",
                "charcoal-teal",
                "navy-teal",
                "blue-silver",
                "sky-orange",
                "indigo-amber",
                "indigo-coral",
                "slate-crimson");

        for (String preset : AppearancePolicy.PRESET_IDS) {
            assertThat(AppearancePolicy.isKnownPreset(preset)).isTrue();
            assertThat(AppearancePolicy.isValidPresetOrCustom(preset)).isTrue();
        }
        assertThat(AppearancePolicy.isCustomPreset("custom")).isTrue();
        assertThat(AppearancePolicy.isValidPresetOrCustom("custom")).isTrue();
        assertThat(AppearancePolicy.isValidPresetOrCustom("unknown-preset")).isFalse();
        assertThat(AppearancePolicy.isValidPresetOrCustom(null)).isFalse();
    }

    @Test
    void validatesHexColorsAndModes() {
        assertThat(AppearancePolicy.isValidHexColor("#059669")).isTrue();
        assertThat(AppearancePolicy.isValidHexColor("#aAbBcC")).isTrue();
        assertThat(AppearancePolicy.isValidHexColor("#12345")).isFalse();
        assertThat(AppearancePolicy.isValidHexColor("059669")).isFalse();
        assertThat(AppearancePolicy.isValidHexColor("#GGGGGG")).isFalse();
        assertThat(AppearancePolicy.isValidHexColor(null)).isFalse();

        assertThat(AppearancePolicy.isValidMode("light")).isTrue();
        assertThat(AppearancePolicy.isValidMode("dark")).isTrue();
        assertThat(AppearancePolicy.isValidMode("system")).isFalse();
        assertThat(AppearancePolicy.isValidMode(null)).isFalse();
    }
}
