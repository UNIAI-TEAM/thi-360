import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { UsersApiError } from "../api";
import {
  KNOWN_USER_ERROR_CODES,
  mapUserErrorCodeToKey,
  resolveUsersErrorMessage,
} from "../utils";

describe("D/T07 error code mapping", () => {
  it("mọi mã lỗi D/T07 đều có thông báo trong cả vi.json và en.json", () => {
    const viCodes = vi.users.errorCodes as Record<string, string>;
    const enCodes = en.users.errorCodes as Record<string, string>;

    for (const code of KNOWN_USER_ERROR_CODES) {
      expect(mapUserErrorCodeToKey(code)).toBe(code);
      expect(typeof viCodes[code]).toBe("string");
      expect(viCodes[code]!.trim().length).toBeGreaterThan(0);
      expect(typeof enCodes[code]).toBe("string");
      expect(enCodes[code]!.trim().length).toBeGreaterThan(0);
    }
  });

  it("resolveUsersErrorMessage ánh xạ mã lỗi trực tiếp, fieldErrors và HTTP status fallback", () => {
    const translate = (code: (typeof KNOWN_USER_ERROR_CODES)[number]) =>
      vi.users.errorCodes[code];

    expect(
      resolveUsersErrorMessage(
        new UsersApiError(409, "raw", "SELF_DEACTIVATE"),
        translate,
        "fallback",
      ),
    ).toBe(vi.users.errorCodes.SELF_DEACTIVATE);

    expect(
      resolveUsersErrorMessage(
        new UsersApiError(409, "raw", "USER_HAS_TRANSACTIONS"),
        translate,
        "fallback",
      ),
    ).toBe(vi.users.errorCodes.USER_HAS_TRANSACTIONS);

    expect(
      resolveUsersErrorMessage(
        new UsersApiError(400, "raw", "UNKNOWN_TOP_CODE", [
          { field: "password", code: "PASSWORD_SAME_AS_USERNAME", message: "raw" },
        ]),
        translate,
        "fallback",
      ),
    ).toBe(vi.users.errorCodes.PASSWORD_SAME_AS_USERNAME);

    expect(
      resolveUsersErrorMessage(new UsersApiError(404, "missing"), translate, "fallback"),
    ).toBe(vi.users.errorCodes.NOT_FOUND);

    expect(resolveUsersErrorMessage(new Error("boom"), translate, "fallback")).toBe("fallback");
  });
});
