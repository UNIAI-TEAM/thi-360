import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AbstractIntlMessages } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { ClassesApiError, type UserClassesResponse } from "../api";
import { UserClassesTab } from "../components/user-classes-tab";

const USER_ID = "00000000-0000-4000-8000-000000000101";

const SAMPLE_CLASSES: UserClassesResponse = {
  userId: USER_ID,
  currentClasses: [
    {
      classId: "00000000-0000-4000-8000-000000000301",
      classCode: "LOP-A",
      className: "Lớp A",
      classStatus: "ACTIVE",
      joinedAt: "2026-11-10T08:00:00Z",
      addedBy: "00000000-0000-4000-8000-000000000002",
      addedByUsername: "ops.admin",
    },
  ],
  history: [
    {
      id: "hist-1",
      occurredAt: "2026-11-10T08:00:00Z",
      action: "JOIN",
      classId: "00000000-0000-4000-8000-000000000301",
      classCode: "LOP-A",
      className: "Lớp A",
      reason: "Nhập học",
      actorId: "00000000-0000-4000-8000-000000000002",
      actorUsername: "ops.admin",
    },
    {
      id: "hist-2",
      occurredAt: "2026-11-11T09:00:00Z",
      action: "TRANSFER_OUT",
      classId: "00000000-0000-4000-8000-000000000301",
      classCode: "LOP-A",
      className: "Lớp A",
      relatedClassId: "00000000-0000-4000-8000-000000000302",
      relatedClassCode: "LOP-B",
      relatedClassName: "Lớp B",
      reason: "Chuyển lớp",
      actorUsername: "ops.admin",
    },
  ],
};

function renderTab(
  getUserClasses: () => Promise<UserClassesResponse>,
  session: Session = TEST_ADMIN_SESSION,
  options: { locale?: string; messages?: AbstractIntlMessages } = {},
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <UserClassesTab userId={USER_ID} getUserClasses={getUserClasses} />
    </SessionProvider>,
    options,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("UserClassesTab", () => {
  it("hiển thị lớp hiện tại và lịch sử với đúng action labels", async () => {
    renderTab(async () => SAMPLE_CLASSES);

    expect(await screen.findByText("LOP-A")).toBeInTheDocument();
    expect(screen.getByText("Lớp học hiện tại")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử tham gia và chuyển lớp")).toBeInTheDocument();
    expect(screen.getAllByText("Vào lớp").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Chuyển đi")).toBeInTheDocument();
    expect(screen.getByText("LOP-B — Lớp B")).toBeInTheDocument();
  });

  it("trạng thái đang tải → skeleton, không hiển thị empty state", async () => {
    renderTab(() => new Promise<UserClassesResponse>(() => {}));

    expect(await screen.findByRole("status")).toBeInTheDocument();
    expect(screen.queryByText("Chưa tham gia lớp nào")).not.toBeInTheDocument();
  });

  it("trạng thái lỗi → EmptyState + nút Thử lại refetch", async () => {
    const user = userEvent.setup();
    const getUserClassesMock = vi
      .fn<() => Promise<UserClassesResponse>>()
      .mockRejectedValueOnce(new ClassesApiError(500, "boom"))
      .mockResolvedValueOnce(SAMPLE_CLASSES);

    renderTab(getUserClassesMock);

    await screen.findByText("Không tải được dữ liệu lớp học");

    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("LOP-A");
    expect(getUserClassesMock).toHaveBeenCalledTimes(2);
  });

  it("trạng thái rỗng → EmptyState cho cả lớp hiện tại và lịch sử", async () => {
    renderTab(async () => ({ userId: USER_ID, currentClasses: [], history: [] }));

    await screen.findByText("Chưa tham gia lớp nào");
    expect(screen.getByText("Chưa có lịch sử lớp học")).toBeInTheDocument();
  });

  it("thiếu quyền CLASS.VIEW → EmptyState không có quyền", async () => {
    const getUserClassesMock = vi.fn(async () => SAMPLE_CLASSES);
    renderTab(getUserClassesMock, {
      ...TEST_ADMIN_SESSION,
      permissions: TEST_ADMIN_SESSION.permissions.filter((p) => p !== "CLASS.VIEW"),
    });

    await screen.findByText("Không có quyền xem thông tin lớp học");
    expect(getUserClassesMock).not.toHaveBeenCalled();
  });

  it("Locale en: hiển thị tiếng Anh, không có text tiếng Việt", async () => {
    const enClasses: UserClassesResponse = {
      userId: USER_ID,
      currentClasses: [
        {
          classId: "cls-en",
          classCode: "ENG-A",
          className: "English Class",
          classStatus: "ACTIVE",
          joinedAt: "2026-11-10T08:00:00Z",
        },
      ],
      history: [
        {
          id: "hist-en",
          occurredAt: "2026-11-10T08:00:00Z",
          action: "JOIN",
          classId: "cls-en",
          classCode: "ENG-A",
          className: "English Class",
          actorUsername: "ops.admin",
        },
      ],
    };

    renderTab(async () => enClasses, TEST_ADMIN_SESSION, { locale: "en", messages: en });

    expect(await screen.findByText("ENG-A")).toBeInTheDocument();
    expect(screen.getByText("Current classes")).toBeInTheDocument();
    expect(screen.getByText("Class membership history")).toBeInTheDocument();
    const bodyText = document.body.textContent ?? "";
    for (const viText of ["Lớp học hiện tại", "Lịch sử tham gia", "học viên", "Chưa tham gia"]) {
      expect(bodyText).not.toContain(viText);
    }
  });
});

describe("UserClassesTab: khung hẹp thành danh sách thẻ (UNI-2587)", () => {
  it("lớp hiện tại: thẻ có mã lớp dẫn tới chi tiết, tên lớp, chip trạng thái lớp, ngày vào lớp và người thêm", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderTab(async () => SAMPLE_CLASSES);

    const list = await screen.findByRole("list", { name: "Lớp học hiện tại của tài khoản" });
    expect(screen.queryByRole("table")).toBeNull();
    const [card] = within(list).getAllByRole("listitem");
    const link = within(card!).getByRole("link", { name: "LOP-A" });
    expect(link).toHaveAttribute("href", "/classes/00000000-0000-4000-8000-000000000301");
    expect(link).toHaveClass("after:absolute", "after:inset-0");
    expect(within(card!).getByText("Lớp A")).toHaveClass("text-caption");
    expect(within(card!).getByText("Hoạt động").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(card).toHaveTextContent("Vào lớp: 10/11/2026 · Người thêm: ops.admin");
  });

  it("lịch sử: thẻ có lớp, thời gian, chip hành động; lớp liên quan là liên kết riêng nằm trên vùng bấm của thẻ", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderTab(async () => SAMPLE_CLASSES);

    const list = await screen.findByRole("list", { name: "Lịch sử tham gia và chuyển lớp" });
    const [join, transfer] = within(list).getAllByRole("listitem");
    expect(within(join!).getByRole("link", { name: "LOP-A — Lớp A" })).toHaveClass("after:absolute");
    expect(within(join!).getByText("Vào lớp").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(join).toHaveTextContent("Người thực hiện: ops.admin · Lý do: Nhập học");
    expect(join).not.toHaveTextContent("Lớp liên quan");

    expect(within(transfer!).getByText("Chuyển đi").closest("[data-tone]")).toHaveAttribute("data-tone", "info");
    const related = within(transfer!).getByRole("link", { name: "LOP-B — Lớp B" });
    expect(related).toHaveAttribute("href", "/classes/00000000-0000-4000-8000-000000000302");
    expect(related).toHaveClass("relative", "z-1");
    expect(transfer).toHaveTextContent("Lớp liên quan: LOP-B — Lớp B · Người thực hiện: ops.admin · Lý do: Chuyển lớp");
  });

  it("rỗng: thông báo rỗng nằm trọn trong danh sách của từng thẻ", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderTab(async () => ({ userId: USER_ID, currentClasses: [], history: [] }));

    const current = await screen.findByRole("list", { name: "Lớp học hiện tại của tài khoản" });
    expect(within(current).getByText("Chưa tham gia lớp nào")).toBeInTheDocument();
    const history = screen.getByRole("list", { name: "Lịch sử tham gia và chuyển lớp" });
    expect(within(history).getByText("Chưa có lịch sử lớp học")).toBeInTheDocument();
  });

  it("khung rộng: vẫn là hai bảng như trước", async () => {
    installContainerWidth(900);
    renderTab(async () => SAMPLE_CLASSES);

    expect(await screen.findByRole("table", { name: "Lớp học hiện tại của tài khoản" })).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Lịch sử tham gia và chuyển lớp" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Lớp học hiện tại của tài khoản" })).toBeNull();
  });
});
