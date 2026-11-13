import { describe, expect, it } from "vitest";
import { buildLoginUrl, resolveHomeUrl, validateCallbackUrl } from "./redirect";

describe("redirect helpers", () => {
  it("chấp nhận đường dẫn tương đối an toàn và origin hợp lệ", () => {
    const allowed = ["http://localhost:3000", "http://localhost:3001"];
    expect(validateCallbackUrl("/admin/x", allowed)).toBe("/admin/x");
    expect(validateCallbackUrl("http://localhost:3001/users?tab=1", allowed)).toBe(
      "http://localhost:3001/users?tab=1",
    );
  });

  it("từ chối open redirect, scheme lạ, userinfo và double-encoding", () => {
    const allowed = ["http://localhost:3000", "http://localhost:3001"];
    expect(validateCallbackUrl("https://evil.example", allowed)).toBeNull();
    expect(validateCallbackUrl("//evil.example", allowed)).toBeNull();
    expect(validateCallbackUrl("/\\evil", allowed)).toBeNull();
    expect(validateCallbackUrl("javascript:alert(1)", allowed)).toBeNull();
    expect(validateCallbackUrl("http://localhost:3000@evil.example", allowed)).toBeNull();
    expect(validateCallbackUrl("/%252f/evil.example", allowed)).toBeNull();
    expect(validateCallbackUrl("", allowed)).toBeNull();
  });

  it("dựng homeUrl và loginUrl đúng chuẩn", () => {
    expect(resolveHomeUrl("MANAGE", { webUrl: "http://localhost:3000", adminUrl: "http://localhost:3001" })).toBe(
      "http://localhost:3001",
    );
    expect(resolveHomeUrl("EXAM", { webUrl: "http://localhost:3000", adminUrl: "http://localhost:3001" })).toBe(
      "http://localhost:3000/home",
    );
    expect(buildLoginUrl("http://localhost:3000", "http://localhost:3001/users")).toBe(
      "http://localhost:3000/dang-nhap?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Fusers",
    );
  });
});
