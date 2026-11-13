import { isValidHexColor, type ThemeMode } from "./palettes";
import { deltaEOklab, deriveTokens } from "./scale";

export const DANGER_REFERENCE_COLOR = "#DC2626";
export const NEAR_DANGER_DELTA_E = 12;
export const SIMILAR_COLORS_DELTA_E = 12;

export type AppearanceWarningCode =
  | "PRIMARY_USES_DARK_TEXT"
  | "NEAR_DANGER_COLOR"
  | "PRIMARY_ACCENT_SIMILAR";

export interface AppearanceWarning {
  code: AppearanceWarningCode;
  target: "primary" | "accent" | "both";
}

/**
 * Kiểm tra cảnh báo khi tự tùy chỉnh màu (spec §13.1 mục D).
 * Không chặn lưu; trả danh sách rỗng nếu mã màu chưa hợp lệ.
 */
export function checkPaletteWarnings({
  primary,
  accent,
  mode = "light",
}: {
  primary: string;
  accent: string;
  mode?: ThemeMode;
}): AppearanceWarning[] {
  if (!isValidHexColor(primary) || !isValidHexColor(accent)) {
    return [];
  }

  const warnings: AppearanceWarning[] = [];
  const tokens = deriveTokens({ primary, accent, mode });

  if (tokens.onPrimary.toLowerCase() !== "#ffffff") {
    warnings.push({
      code: "PRIMARY_USES_DARK_TEXT",
      target: "primary",
    });
  }

  const primaryNearDanger = deltaEOklab(primary, DANGER_REFERENCE_COLOR) < NEAR_DANGER_DELTA_E;
  const accentNearDanger = deltaEOklab(accent, DANGER_REFERENCE_COLOR) < NEAR_DANGER_DELTA_E;
  if (primaryNearDanger || accentNearDanger) {
    warnings.push({
      code: "NEAR_DANGER_COLOR",
      target:
        primaryNearDanger && accentNearDanger ? "both" : primaryNearDanger ? "primary" : "accent",
    });
  }

  if (deltaEOklab(primary, accent) < SIMILAR_COLORS_DELTA_E) {
    warnings.push({
      code: "PRIMARY_ACCENT_SIMILAR",
      target: "both",
    });
  }

  return warnings;
}
