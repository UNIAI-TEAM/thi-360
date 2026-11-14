import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AbstractIntlMessages } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
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
