import { INTL_FORMATS } from "@thi360/web-kit/preferences";
import { createFormatter } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import en from "../messages/en.json";
import viMessages from "../messages/vi.json";

const cookieValues = vi.hoisted(() => ({ locale: undefined as string | undefined }));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "thi360_locale" && cookieValues.locale !== undefined
        ? { value: cookieValues.locale }
        : undefined,
  }),
}));

vi.mock("next-intl/server", () => ({
  getRequestConfig: (factory: () => unknown) => factory,
}));

import requestConfig from "./request";

describe("web-student i18n request", () => {
  it("mặc định vi khi thiếu cookie hoặc cookie sai giá trị và định dạng dd/MM/yyyy + dấu chấm nghìn", async () => {
    cookieValues.locale = undefined;
    const missing = await (
      requestConfig as unknown as () => Promise<{
        locale: string;
        messages: Record<string, unknown>;
        timeZone: string;
        formats: typeof INTL_FORMATS;
      }>
    )();
    expect(missing.locale).toBe("vi");
    expect(missing.messages).toEqual(viMessages);
    expect(missing.formats).toEqual(INTL_FORMATS);

    const viFormat = createFormatter(missing);
    const sampleDate = new Date("2026-11-05T03:00:00Z");
    expect(viFormat.dateTime(sampleDate, "short")).toBe("05/11/2026");
    expect(viFormat.dateTime(sampleDate, "date")).toBe("05/11/2026");
    expect(viFormat.number(1234567)).toBe("1.234.567");

    cookieValues.locale = "jp";
    const invalid = await (
      requestConfig as unknown as () => Promise<{ locale: string; messages: unknown }>
    )();
    expect(invalid.locale).toBe("vi");
    expect(invalid.messages).toEqual(viMessages);
  });

  it("trả locale=en-GB, trộn thông điệp en lên vi và định dạng dd/MM/yyyy + dấu phẩy nghìn khi thi360_locale=en", async () => {
    cookieValues.locale = "en";
    const result = await (
      requestConfig as unknown as () => Promise<{
        locale: string;
        messages: Record<string, unknown>;
        timeZone: string;
        formats: typeof INTL_FORMATS;
      }>
    )();
    expect(result.locale).toBe("en-GB");
    expect(result.messages).toEqual(en);
    expect(result.formats).toEqual(INTL_FORMATS);

    const enFormat = createFormatter(result);
    const sampleDate = new Date("2026-11-05T03:00:00Z");
    expect(enFormat.dateTime(sampleDate)).toBe("05/11/2026");
    expect(enFormat.dateTime(sampleDate, "short")).toBe("05/11/2026");
    expect(enFormat.dateTime(sampleDate, "date")).toBe("05/11/2026");
    expect(enFormat.number(1234567)).toBe("1,234,567");
  });
});
