import { type AppearanceConfig, type AppearancePalette, resolvePaletteColors, type ThemeMode } from "./palettes";
import { deriveTokens } from "./scale";

export const THEME_STYLE_ELEMENT_ID = "thi360-theme-vars";

/**
 * Trả map biến CSS (`--color-*`, `--shadow-primary`) để gắn vào vùng xem trước cục bộ.
 */
export function buildThemeVars(config: AppearanceConfig): Record<`--${string}`, string> {
  const { primary, accent } = resolvePaletteColors(config.palette);
  return deriveTokens({ primary, accent, mode: config.mode }).vars;
}

function formatDeclarations(vars: Record<`--${string}`, string>): string {
  return Object.entries(vars)
    .map(([name, value]) => `${name}: ${value};`)
    .join(" ");
}

/**
 * Sinh chuỗi CSS chứa đầy đủ biến cho cả hai chế độ sáng/tối theo thứ tự cố định,
 * an toàn để nhúng vào thẻ `<style>` trong `<head>`.
 */
export function themeStyleTag(config: {
  palette: AppearancePalette;
  mode?: ThemeMode;
  version?: number;
}): string {
  const lightVars = buildThemeVars({ palette: config.palette, mode: "light" });
  const darkVars = buildThemeVars({ palette: config.palette, mode: "dark" });
  return `:root[data-theme="light"] { ${formatDeclarations(lightVars)} }\n:root[data-theme="dark"] { ${formatDeclarations(darkVars)} }`;
}
