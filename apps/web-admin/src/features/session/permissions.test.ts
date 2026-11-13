import { describe, expect, it } from "vitest";
import { hasPermission } from "./permissions";

describe("hasPermission", () => {
  const granted = new Set(["USER.VIEW", "ROLE.VIEW"]);

  it("một mã: có hoặc không", () => {
    expect(hasPermission(granted, "USER.VIEW")).toBe(true);
    expect(hasPermission(granted, "USER.MODIFY")).toBe(false);
  });

  it("danh sách: đủ khi có một trong các mã", () => {
    expect(hasPermission(granted, ["USER.MODIFY", "ROLE.VIEW"])).toBe(true);
    expect(hasPermission(granted, ["USER.MODIFY", "CLASS.VIEW"])).toBe(false);
  });

  it("danh sách rỗng không cấp quyền", () => {
    expect(hasPermission(granted, [])).toBe(false);
  });
});
