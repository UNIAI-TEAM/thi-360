import { type AppearanceConfig, resolvePaletteColors } from "./palettes";
import { deriveTokens } from "./scale";

/**
 * Trả map biến CSS (`--color-*`, `--shadow-primary`) để gắn vào `<html style>` hoặc vùng xem trước.
 */
export function buildThemeVars(config: AppearanceConfig): Record<`--${string}`, string> {
  const { primary, accent } = resolvePaletteColors(config.palette);
  return deriveTokens({ primary, accent, mode: config.mode }).vars;
}

/**
 * Sinh chuỗi CSS chứa toàn bộ biến của cấu hình giao diện, an toàn để nhúng vào thẻ `<style>`.
 */
export function themeStyleTag(config: AppearanceConfig): string {
  const vars = buildThemeVars(config);
  const declarations = Object.entries(vars)
    .map(([name, value]) => `${name}: ${value};`)
    .join(" ");
  return `:root { ${declarations} }`;
}
