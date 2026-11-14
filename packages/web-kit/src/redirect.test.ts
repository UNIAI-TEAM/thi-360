import { describe, expect, it } from "vitest";
import { buildLoginUrl, resolveAuthenticatedLanding, resolveHomeUrl, validateCallbackUrl } from "./redirect";

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

describe("resolveAuthenticatedLanding", () => {
  const urls = { webUrl: "https://thi360.vn", adminUrl: "https://admin.thi360.vn" };

  it("callbackUrl hợp lệ của web admin hoặc web học viên được dùng nguyên văn", () => {
    expect(resolveAuthenticatedLanding("MANAGE", "https://admin.thi360.vn/lop-hoc?page=2", urls)).toBe(
      "https://admin.thi360.vn/lop-hoc?page=2",
    );
    expect(resolveAuthenticatedLanding("EXAM", "/home?tab=1", urls)).toBe("https://thi360.vn/home?tab=1");
  });

  it("thiếu callbackUrl, callbackUrl ngoài hệ thống hoặc trỏ về chính trang đăng nhập: về trang chủ theo quyền", () => {
    expect(resolveAuthenticatedLanding("MANAGE", null, urls)).toBe("https://admin.thi360.vn");
    expect(resolveAuthenticatedLanding("EXAM", "https://trang-la.example/x", urls)).toBe("https://thi360.vn/home");
    expect(resolveAuthenticatedLanding("EXAM", "//trang-la.example", urls)).toBe("https://thi360.vn/home");
    expect(resolveAuthenticatedLanding("MANAGE", "/dang-nhap?callbackUrl=%2Fhome", urls)).toBe(
      "https://admin.thi360.vn",
    );
    expect(resolveAuthenticatedLanding("MANAGE", "https://thi360.vn/dang-nhap", urls)).toBe("https://admin.thi360.vn");
  });

  it("đường dẫn chuẩn hóa thành //host không được dùng làm đích (open redirect qua Location tương đối)", () => {
    for (const raw of ["/.//evil.example", "/..//evil.example", "/a/..//evil.example", "/%2E//evil.example"]) {
      expect(validateCallbackUrl(raw, [urls.webUrl, urls.adminUrl])).toBeNull();
      expect(resolveAuthenticatedLanding("EXAM", raw, urls)).toBe("https://thi360.vn/home");
    }
    expect(resolveAuthenticatedLanding("MANAGE", "https://thi360.vn//evil.example", urls)).toBe(
      "https://admin.thi360.vn",
    );
    expect(resolveAuthenticatedLanding("MANAGE", "https://admin.thi360.vn//evil.example/x", urls)).toBe(
      "https://admin.thi360.vn",
    );
    // Đường dẫn bình thường có `..` ở giữa vẫn hợp lệ.
    expect(resolveAuthenticatedLanding("EXAM", "/home/../home?tab=1", urls)).toBe("https://thi360.vn/home?tab=1");
  });
});
