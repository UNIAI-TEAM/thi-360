import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import type { ListUsersQuery, RoleSummary, UserSummary } from "@/features/users/api";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  ClassesApiError,
  type ClassesApiDeps,
  type ClassDetail,
  type ClassMember,
  defaultClassesApiDeps,
} from "../api";
import { ClassDetailView } from "../components/class-detail-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

const BASE_CLASS: ClassDetail = {
  id: "00000000-0000-4000-8000-000000000301",
  code: "LOP-A",
  name: "Lớp A",
  description: "Nhóm ôn HSA buổi tối",
  ownerUserId: "00000000-0000-4000-8000-000000000002",
  ownerUsername: "ops.admin",
  ownerFullName: "Vận hành CSKH",
  status: "ACTIVE",
  memberCount: 2,
  version: 0,
  createdAt: "2026-11-10T08:00:00Z",
  updatedAt: "2026-11-10T08:00:00Z",
};

const SAMPLE_MEMBERS: ClassMember[] = [
  {
    userId: "00000000-0000-4000-8000-000000000101",
    userCode: "U000101",
    username: "student01",
    fullName: "Học viên 01",
    userStatus: "ACTIVE",
    joinedAt: "2026-11-10T08:00:00Z",
  },
  {
    userId: "00000000-0000-4000-8000-000000000102",
    userCode: "U000102",
    username: "student02",
    fullName: "Học viên 02",
    userStatus: "ACTIVE",
    joinedAt: "2026-11-10T08:00:00Z",
  },
];

const STUDENT_CANDIDATES: UserSummary[] = [
  {
    id: "00000000-0000-4000-8000-000000000101",
    code: "U000101",
    username: "student01",
    fullName: "Học viên 01",
    source: "ADMIN_CREATED",
    status: "ACTIVE",
    mustChangePassword: false,
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 0,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
  {
    id: "00000000-0000-4000-8000-000000000103",
    code: "U000103",
    username: "student03",
    fullName: "Học viên 03",
    source: "ADMIN_CREATED",
    status: "ACTIVE",
    mustChangePassword: false,
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 0,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
  {
    id: "00000000-0000-4000-8000-000000000900",
    code: "U000900",
    username: "ops.admin",
    fullName: "Vận hành CSKH",
    source: "ADMIN_CREATED",
    status: "ACTIVE",
    mustChangePassword: false,
    roles: [{ id: "r-ops", code: "OPERATIONS", name: "Vận hành", access: "MANAGE" }],
    version: 0,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
];

const ROLES: RoleSummary[] = [
  {
    id: "r-student",
    code: "STUDENT",
    name: "Học viên",
    access: "EXAM",
    status: "ACTIVE",
    system: true,
    permissionCount: 0,
    userCount: 3,
    version: 0,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
];

function makeDeps(overrides: Partial<ClassesApiDeps> = {}): Partial<ClassesApiDeps> {
  return {
    ...defaultClassesApiDeps,
    getClass: vi.fn<ClassesApiDeps["getClass"]>(async () => BASE_CLASS),
    listClassMembers: vi.fn<ClassesApiDeps["listClassMembers"]>(async () => ({
      items: SAMPLE_MEMBERS,
      page: 0,
      size: 20,
      totalItems: SAMPLE_MEMBERS.length,
      totalPages: 1,
    })),
    listAllClasses: vi.fn<ClassesApiDeps["listAllClasses"]>(async () => [
      {
        id: BASE_CLASS.id,
        code: BASE_CLASS.code,
        name: BASE_CLASS.name,
        status: "ACTIVE",
        memberCount: 2,
        version: 0,
        createdAt: BASE_CLASS.createdAt,
        updatedAt: BASE_CLASS.updatedAt,
      },
      {
        id: "00000000-0000-4000-8000-000000000302",
        code: "LOP-B",
        name: "Lớp B",
        status: "ACTIVE",
        memberCount: 1,
        version: 0,
        createdAt: BASE_CLASS.createdAt,
        updatedAt: BASE_CLASS.updatedAt,
      },
      {
        id: "00000000-0000-4000-8000-000000000303",
        code: "LOP-OLD",
        name: "Lớp cũ",
        status: "INACTIVE",
        memberCount: 0,
        version: 0,
        createdAt: BASE_CLASS.createdAt,
        updatedAt: BASE_CLASS.updatedAt,
      },
    ]),
    addClassMembers: vi.fn<ClassesApiDeps["addClassMembers"]>(async (id, body) => ({
      classId: id,
      addedUserIds: body.userIds,
      skippedUserIds: [],
      addedCount: body.userIds.length,
      skippedCount: 0,
    })),
    removeClassMember: vi.fn<ClassesApiDeps["removeClassMember"]>(async () => undefined),
    transferClassMember: vi.fn<ClassesApiDeps["transferClassMember"]>(async (id, userId, body) => ({
      sourceClassId: id,
      targetClassId: body.targetClassId,
      userId,
      transferredAt: "2026-11-14T00:00:00Z",
    })),
    ...overrides,
  };
}

function renderDetail(
  deps: Partial<ClassesApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
  listUsersMock?: (query: ListUsersQuery) => Promise<{
    items: UserSummary[];
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
  }>,
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <ClassDetailView
        classId={BASE_CLASS.id}
        deps={deps}
        listActiveRolesForAdd={async () => ROLES}
        listUsersForAdd={
          listUsersMock ??
          (async () => ({
            items: STUDENT_CANDIDATES,
            page: 0,
            size: 20,
            totalItems: STUDENT_CANDIDATES.length,
            totalPages: 1,
          }))
        }
      />
    </SessionProvider>,
  );
}

afterEach(() => {
  routerMock.push.mockReset();
  vi.restoreAllMocks();
});

describe("ClassDetailView", () => {
  it("hiển thị thông tin lớp, bảng thành viên và KHÔNG có khối Lịch sử thành viên", async () => {
    renderDetail(makeDeps());

    expect(await screen.findByRole("heading", { name: "Lớp A" })).toBeInTheDocument();
    expect(screen.getByText(/LOP-A · Nhóm ôn HSA buổi tối/)).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "student01" })).toHaveAttribute(
      "href",
      `/tai-khoan/${SAMPLE_MEMBERS[0]!.userId}`,
    );
    expect(screen.queryByText(/Lịch sử thành viên/i)).not.toBeInTheDocument();
  });

  it("người chỉ có CLASS.VIEW không thấy các nút sửa, ngừng sử dụng, xóa, thêm, chuyển và loại học viên", async () => {
    renderDetail(makeDeps(), { ...TEST_ADMIN_SESSION, permissions: ["CLASS.VIEW"] });

    expect(await screen.findByRole("heading", { name: "Lớp A" })).toBeInTheDocument();
    expect(await screen.findByText("student01")).toBeInTheDocument();

    expect(screen.queryByRole("button", { name: "Sửa thông tin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ngừng sử dụng" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa lớp" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thêm học viên/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Chuyển student01/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Loại student01/ })).not.toBeInTheDocument();
  });

  it("lớp INACTIVE vô hiệu hóa nút Thêm học viên và hiện giải thích", async () => {
    renderDetail(
      makeDeps({
        getClass: vi.fn(async () => ({ ...BASE_CLASS, status: "INACTIVE" as const })),
      }),
    );

    expect(await screen.findByRole("heading", { name: "Lớp A" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Thêm học viên/ })).toBeDisabled();
    expect(
      screen.getByText(/Lớp đang ngừng sử dụng: chức năng thêm hoặc chuyển học viên vào lớp này đã bị vô hiệu hóa/),
    ).toBeInTheDocument();
  });

  it("dialog thêm học viên chỉ hiện học viên (STUDENT), báo skippedCount và hiện mã lỗi CLASS_INACTIVE", async () => {
    const addMock = vi
      .fn<ClassesApiDeps["addClassMembers"]>()
      .mockResolvedValueOnce({
        classId: BASE_CLASS.id,
        addedUserIds: [STUDENT_CANDIDATES[1]!.id],
        skippedUserIds: [STUDENT_CANDIDATES[0]!.id],
        addedCount: 1,
        skippedCount: 1,
      })
      .mockRejectedValueOnce(new ClassesApiError(409, "inactive", "CLASS_INACTIVE"));

    const listUsersMock = vi.fn(async () => ({
      items: STUDENT_CANDIDATES,
      page: 0,
      size: 20,
      totalItems: STUDENT_CANDIDATES.length,
      totalPages: 1,
    }));

    renderDetail(makeDeps({ addClassMembers: addMock }), TEST_ADMIN_SESSION, listUsersMock);

    await screen.findByRole("heading", { name: "Lớp A" });
    await userEvent.click(screen.getByRole("button", { name: /Thêm học viên/ }));

    const dialog = screen.getByRole("dialog");
    expect(await within(dialog).findByText("student03")).toBeInTheDocument();
    expect(within(dialog).getByText("student01")).toBeInTheDocument();
    expect(within(dialog).queryByText("ops.admin")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenCalledWith(
        expect.objectContaining({ status: "ACTIVE", roleId: "r-student" }),
      );
    });

    await userEvent.click(within(dialog).getByRole("checkbox", { name: /student01/ }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /student03/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Thêm 2 học viên" }));

    await waitFor(() => {
      expect(addMock).toHaveBeenCalledWith(BASE_CLASS.id, {
        userIds: [STUDENT_CANDIDATES[0]!.id, STUDENT_CANDIDATES[1]!.id],
      });
    });

    // Mở lại để kiểm tra thông báo lỗi 409 CLASS_INACTIVE theo message key
    await userEvent.click(screen.getByRole("button", { name: /Thêm học viên/ }));
    const dialog2 = screen.getByRole("dialog");
    await userEvent.click(await within(dialog2).findByRole("checkbox", { name: /student03/ }));
    await userEvent.click(within(dialog2).getByRole("button", { name: "Thêm 1 học viên" }));

    expect(
      await within(dialog2).findByText(
        "Lớp học đang ngừng sử dụng, không thể thêm hoặc chuyển học viên vào.",
      ),
    ).toBeInTheDocument();
  });

  it("dialog chuyển lớp vô hiệu hóa lớp INACTIVE, gửi targetClassId + reason và hiển thị 409 CLASS_INACTIVE", async () => {
    const transferMock = vi
      .fn<ClassesApiDeps["transferClassMember"]>()
      .mockRejectedValueOnce(new ClassesApiError(409, "inactive", "CLASS_INACTIVE"))
      .mockResolvedValueOnce({
        sourceClassId: BASE_CLASS.id,
        targetClassId: "00000000-0000-4000-8000-000000000302",
        userId: SAMPLE_MEMBERS[0]!.userId,
        transferredAt: "2026-11-14T00:00:00Z",
      });

    renderDetail(makeDeps({ transferClassMember: transferMock }));

    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("button", { name: "Chuyển student01 sang lớp khác" }));

    const dialog = screen.getByRole("dialog");
    expect(
      await within(dialog).findByText(/Các lớp đang ngừng sử dụng bị vô hiệu hóa/),
    ).toBeInTheDocument();

    const targetSelect = within(dialog).getByRole("combobox", { name: /Lớp đích/ });
    await userEvent.click(targetSelect);

    const inactiveOption = await screen.findByRole("option", { name: /LOP-OLD/ });
    expect(inactiveOption).toHaveAttribute("aria-disabled", "true");

    await userEvent.click(screen.getByRole("option", { name: "LOP-B — Lớp B" }));
    await userEvent.type(within(dialog).getByLabelText(/Lý do chuyển lớp/), "Đổi ca học");
    await userEvent.click(within(dialog).getByRole("button", { name: "Chuyển lớp" }));

    expect(
      await within(dialog).findByText(
        "Lớp học đang ngừng sử dụng, không thể thêm hoặc chuyển học viên vào.",
      ),
    ).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Chuyển lớp" }));
    await waitFor(() => {
      expect(transferMock).toHaveBeenLastCalledWith(BASE_CLASS.id, SAMPLE_MEMBERS[0]!.userId, {
        targetClassId: "00000000-0000-4000-8000-000000000302",
        reason: "Đổi ca học",
      });
    });
  });

  it("dialog loại khỏi lớp gửi reason khi xác nhận", async () => {
    const removeMock = vi.fn(async () => undefined);
    renderDetail(makeDeps({ removeClassMember: removeMock }));

    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("button", { name: "Loại student01 khỏi lớp" }));

    const dialog = screen.getByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/Lý do loại khỏi lớp/), "Hoàn thành khóa");
    await userEvent.click(within(dialog).getByRole("button", { name: "Loại khỏi lớp" }));

    await waitFor(() => {
      expect(removeMock).toHaveBeenCalledWith(
        BASE_CLASS.id,
        SAMPLE_MEMBERS[0]!.userId,
        "Hoàn thành khóa",
      );
    });
  });
});
