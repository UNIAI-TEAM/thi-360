import { describe, expect, it } from "vitest";
import { keepPreviousWhileSameScope } from "./keep-previous";

describe("keepPreviousWhileSameScope", () => {
  const previousData = { items: ["a"] };

  it("chỉ tham số (phần tử cuối của khóa) đổi: giữ dữ liệu của truy vấn trước", () => {
    const keep = keepPreviousWhileSameScope(["users", "list", { neverLoggedIn: true, page: 0 }]);
    expect(keep(previousData, { queryKey: ["users", "list", { page: 0 }] })).toBe(previousData);
  });

  it("đối tượng đứng trước tham số đổi (lớp khác, nhóm khác): không giữ", () => {
    const keep = keepPreviousWhileSameScope(["classes", "members", "class-2", { page: 0 }]);
    expect(keep(previousData, { queryKey: ["classes", "members", "class-1", { page: 0 }] })).toBeUndefined();
  });

  it("khóa khác độ dài (từ trạng thái chưa chọn đối tượng) hoặc chưa có truy vấn trước: không giữ", () => {
    const keep = keepPreviousWhileSameScope(["catalog", "items", "group-1", { page: 0 }]);
    expect(keep(previousData, { queryKey: ["catalog", "items", "none"] })).toBeUndefined();
    expect(keep(previousData, undefined)).toBeUndefined();
  });
});
