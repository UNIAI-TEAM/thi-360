package vn.unicom.thi360.shared.appearance.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AppearanceSettingPayload(
        Palette palette,
        String mode
) {

    public static AppearanceSettingPayload defaultConfig() {
        return new AppearanceSettingPayload(
                new Palette(AppearancePolicy.DEFAULT_PRESET_ID, null),
                AppearancePolicy.DEFAULT_MODE);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Palette(
            String presetId,
            CustomColors custom
    ) {
    }

    public record CustomColors(
            String primary,
            String accent
    ) {
    }
}
