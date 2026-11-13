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

describe("web-admin i18n request", () => {
  it("mặc định vi khi thiếu cookie hoặc cookie sai giá trị", async () => {
    cookieValues.locale = undefined;
    const missing = await (requestConfig as unknown as () => Promise<{ locale: string; messages: unknown }>)();
    expect(missing.locale).toBe("vi");
    expect(missing.messages).toEqual(viMessages);

    cookieValues.locale = "fr";
    const invalid = await (requestConfig as unknown as () => Promise<{ locale: string; messages: unknown }>)();
    expect(invalid.locale).toBe("vi");
    expect(invalid.messages).toEqual(viMessages);
  });

  it("trả locale=en và trộn thông điệp en lên vi khi thi360_locale=en", async () => {
    cookieValues.locale = "en";
    const result = await (requestConfig as unknown as () => Promise<{ locale: string; messages: unknown }>)();
    expect(result.locale).toBe("en");
    expect(result.messages).toEqual(en);
  });
});
