import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { ClassesApiError } from "../api";
import {
  formatClassDate,
  formatClassDateTime,
  KNOWN_CLASS_ERROR_CODES,
  mapClassErrorCodeToKey,
  resolveClassesErrorMessage,
} from "../utils";

describe("T10 classes error codes & date formatting", () => {
  it("mọi mã lỗi lớp học đều có bản dịch ở cả vi.json và en.json", () => {
    const viCodes = vi.classes.errorCodes as Record<string, string>;
    const enCodes = en.classes.errorCodes as Record<string, string>;

    for (const code of KNOWN_CLASS_ERROR_CODES) {
      expect(mapClassErrorCodeToKey(code)).toBe(code);
      expect(typeof viCodes[code]).toBe("string");
      expect(viCodes[code]!.trim().length).toBeGreaterThan(0);
      expect(typeof enCodes[code]).toBe("string");
      expect(enCodes[code]!.trim().length).toBeGreaterThan(0);
    }
  });

  it("resolveClassesErrorMessage ánh xạ CLASS_INACTIVE, fieldErrors và HTTP status fallback", () => {
    const translate = (code: (typeof KNOWN_CLASS_ERROR_CODES)[number]) =>
      vi.classes.errorCodes[code];

    expect(
      resolveClassesErrorMessage(
        new ClassesApiError(409, "raw", "CLASS_INACTIVE"),
        translate,
        "fallback",
      ),
    ).toBe(vi.classes.errorCodes.CLASS_INACTIVE);

    expect(
      resolveClassesErrorMessage(
        new ClassesApiError(409, "raw", "CLASS_CODE_TAKEN"),
        translate,
        "fallback",
      ),
    ).toBe(vi.classes.errorCodes.CLASS_CODE_TAKEN);

    expect(
      resolveClassesErrorMessage(
        new ClassesApiError(400, "raw", "VALIDATION_FAILED", [
          { field: "reason", code: "REASON_REQUIRED", message: "raw" },
        ]),
        translate,
        "fallback",
      ),
    ).toBe(vi.classes.errorCodes.REASON_REQUIRED);

    expect(
      resolveClassesErrorMessage(new ClassesApiError(403, "forbidden"), translate, "fallback"),
    ).toBe(vi.classes.errorCodes.FORBIDDEN);

    expect(
      resolveClassesErrorMessage(new ClassesApiError(404, "missing"), translate, "fallback"),
    ).toBe(vi.classes.errorCodes.NOT_FOUND);

    expect(resolveClassesErrorMessage(new Error("boom"), translate, "fallback")).toBe("fallback");
  });

  it("định dạng ngày dd/MM/yyyy và dd/MM/yyyy HH:mm ổn định", () => {
    expect(formatClassDate(undefined)).toBe("—");
    expect(formatClassDate("invalid")).toBe("—");
    expect(formatClassDate("2026-11-10T03:15:00Z")).toMatch(/^\d{2}\/\d{2}\/2026$/);
    expect(formatClassDateTime("2026-11-10T03:15:00Z")).toMatch(/^\d{2}\/\d{2}\/2026 \d{2}:\d{2}$/);
  });
});
