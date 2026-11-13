import { describe, expect, it } from "vitest";
import {
  buildPreferenceCookieString,
  deepMergeMessages,
  normalizeCookieDomain,
  normalizeUiLocale,
  parseLocaleCookie,
  parseThemeCookie,
  PREFERENCE_COOKIE_MAX_AGE_SECONDS,
  resolveIntlLocale,
  resolveTheme,
} from "./preferences";

describe("preferences cookies", () => {
  it("parseLocaleCookie chỉ nhận vi/en và rơi về vi khi thiếu hoặc sai", () => {
    expect(parseLocaleCookie("en")).toBe("en");
    expect(parseLocaleCookie(" en ")).toBe("en");
    expect(parseLocaleCookie("vi")).toBe("vi");
    expect(parseLocaleCookie(undefined)).toBe("vi");
    expect(parseLocaleCookie(null)).toBe("vi");
    expect(parseLocaleCookie("")).toBe("vi");
    expect(parseLocaleCookie("fr")).toBe("vi");
    expect(parseLocaleCookie("EN")).toBe("vi");
    expect(parseLocaleCookie("en-GB")).toBe("vi");
  });

  it("resolveIntlLocale ánh xạ en sang en-GB và normalizeUiLocale đưa về vi/en", () => {
    expect(resolveIntlLocale("vi")).toBe("vi");
    expect(resolveIntlLocale("en")).toBe("en-GB");
    expect(normalizeUiLocale("en-GB")).toBe("en");
    expect(normalizeUiLocale("en")).toBe("en");
    expect(normalizeUiLocale("fr")).toBe("vi");
  });

  it("parseThemeCookie chỉ nhận light/dark và trả null khi thiếu hoặc sai", () => {
    expect(parseThemeCookie("light")).toBe("light");
    expect(parseThemeCookie("dark")).toBe("dark");
    expect(parseThemeCookie(" dark ")).toBe("dark");
    expect(parseThemeCookie(undefined)).toBeNull();
    expect(parseThemeCookie(null)).toBeNull();
    expect(parseThemeCookie("")).toBeNull();
    expect(parseThemeCookie("system")).toBeNull();
    expect(parseThemeCookie("DARK")).toBeNull();
  });

  it("resolveTheme ưu tiên cookie hợp lệ, sai hoặc thiếu thì theo chế độ mặc định hệ thống", () => {
    expect(resolveTheme("light", "dark")).toBe("dark");
    expect(resolveTheme("dark", "light")).toBe("light");
    expect(resolveTheme("dark", undefined)).toBe("dark");
    expect(resolveTheme("light", "invalid")).toBe("light");
    expect(resolveTheme("dark", "sepia")).toBe("dark");
  });

  it("buildPreferenceCookieString đặt Max-Age 1 năm, Path=/, SameSite=Lax và Domain khi hợp lệ", () => {
    expect(buildPreferenceCookieString("thi360_locale", "en")).toBe(
      `thi360_locale=en; Max-Age=${PREFERENCE_COOKIE_MAX_AGE_SECONDS}; Path=/; SameSite=Lax`,
    );
    expect(
      buildPreferenceCookieString("thi360_theme", "dark", {
        domain: " .thi360.vn ",
        secure: true,
      }),
    ).toBe(
      `thi360_theme=dark; Max-Age=${PREFERENCE_COOKIE_MAX_AGE_SECONDS}; Path=/; SameSite=Lax; Domain=.thi360.vn; Secure`,
    );
    expect(normalizeCookieDomain("  ")).toBeUndefined();
    expect(normalizeCookieDomain("bad;domain")).toBeUndefined();
  });

  it("deepMergeMessages trộn en lên vi và rơi về vi cho khóa bị thiếu", () => {
    const vi = {
      common: { retry: "Thử lại", loading: "Đang tải" },
      nested: { a: "Một", b: { c: "Ba", d: "Bốn" } },
    };
    const en = {
      common: { retry: "Retry" },
      nested: { b: { c: "Three" } },
    };
    expect(deepMergeMessages(vi, en)).toEqual({
      common: { retry: "Retry", loading: "Đang tải" },
      nested: { a: "Một", b: { c: "Three", d: "Bốn" } },
    });
  });

  it("deepMergeMessages bỏ qua giá trị rỗng, null và lệch kiểu để không che mất chuỗi vi", () => {
    const vi = {
      common: { retry: "Thử lại", loading: "Đang tải", nested: { label: "Nhãn" } },
      title: "Tiêu đề",
    };
    const en = {
      common: { retry: "", loading: "   ", nested: "không-phải-object" },
      title: null,
      extraBlank: "   ",
    } as unknown as Record<string, unknown>;
    expect(deepMergeMessages(vi, en)).toEqual(vi);

    const enObjOverString = { title: { nested: "wrong" } } as unknown as Record<string, unknown>;
    expect(deepMergeMessages(vi, enObjOverString)).toEqual(vi);
  });
});
