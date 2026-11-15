import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import type { AuditEvent, UsersApiDeps } from "../api";
import { UserHistoryTab } from "../components/user-history-tab";

const USER_ID = "00000000-0000-4000-8000-000000000101";

const EVENTS: AuditEvent[] = [
  {
    id: "evt-1",
    occurredAt: "2026-11-12T08:35:00Z",
    actorUsername: "ops.admin",
    actorRoles: ["OPERATIONS"],
    actorType: "USER",
    module: "IDENTITY",
    action: "USER_DEACTIVATED",
    targetType: "USER",
    targetId: USER_ID,
    outcome: "SUCCESS",
    reason: "Nghỉ học",
  },
  {
    id: "evt-2",
    occurredAt: "2026-11-11T07:00:00Z",
    actorRoles: [],
    actorType: "SYSTEM",
    module: "IDENTITY",
    action: "LOGIN_FAILED",
    targetType: "USER",
    targetId: USER_ID,
    outcome: "FAILURE",
  },
];

function renderTab() {
  const listUserAuditEvents = vi.fn<UsersApiDeps["listUserAuditEvents"]>(async () => ({
    items: EVENTS,
    page: 0,
    size: 20,
    totalItems: EVENTS.length,
    totalPages: 1,
  }));
  return renderWithProviders(<UserHistoryTab userId={USER_ID} listUserAuditEvents={listUserAuditEvents} />);
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("UserHistoryTab: khung hẹp thành danh sách thẻ (UNI-2587)", () => {
  it("khung 358px: mỗi sự kiện một thẻ: hành động, thời gian, chip kết quả, người thực hiện và lý do", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderTab();

    const list = await screen.findByRole("list", { name: "Lịch sử thay đổi tài khoản" });
    await within(list).findByText("Ngừng hoạt động tài khoản");
    expect(screen.queryByRole("table")).toBeNull();
    const [first, second] = within(list).getAllByRole("listitem");

    // Sự kiện không có trang chi tiết: tiêu đề là chữ, thẻ không có liên kết hay nút.
    expect(within(first!).queryByRole("link")).toBeNull();
    expect(within(first!).queryByRole("button")).toBeNull();
    expect(within(first!).getByText("Ngừng hoạt động tài khoản")).toHaveClass("line-clamp-2");
    expect(first).toHaveTextContent("12/11/2026");
    expect(within(first!).getByText("Thành công").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(first).toHaveTextContent("Người thực hiện: ops.admin · Lý do: Nghỉ học");

    // Không có người thực hiện thì là Hệ thống; không có lý do thì bỏ mục Lý do.
    expect(within(second!).getByText("Thất bại").closest("[data-tone]")).toHaveAttribute("data-tone", "danger");
    expect(second).toHaveTextContent("Người thực hiện: Hệ thống");
    expect(second).not.toHaveTextContent("Lý do");
  });

  it("khung rộng: vẫn là bảng năm cột như trước", async () => {
    installContainerWidth(900);
    renderTab();

    const table = await screen.findByRole("table", { name: "Lịch sử thay đổi tài khoản" });
    await within(table).findByText("Ngừng hoạt động tài khoản");
    expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
      "Thời gian",
      "Người thực hiện",
      "Hành động",
      "Kết quả",
      "Lý do",
    ]);
    expect(screen.queryByRole("list", { name: "Lịch sử thay đổi tài khoản" })).toBeNull();
  });
});
