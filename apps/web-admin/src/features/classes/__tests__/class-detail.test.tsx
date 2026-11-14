import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import type { ListUsersQuery, RoleSummary, UserSummary } from "@/features/users/api";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import {
  ClassesApiError,
  type ClassesApiDeps,
  type ClassDetail,
  type ClassMember,
  type ClassSummary,
  type ListClassMembersQuery,
  defaultClassesApiDeps,
} from "../api";
import { ClassDetailView } from "../components/class-detail-view";
import { STUDENT_ROLE_ID } from "../components/add-members-dialog";

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
    listClasses: vi.fn<ClassesApiDeps["listClasses"]>(async () => ({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
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
    // Học viên đã thuộc lớp nhưng không nằm trong trang thành viên đang tải: dialog không đánh dấu được, máy chủ báo bỏ qua.
    const unlistedMember: UserSummary = {
      ...STUDENT_CANDIDATES[1]!,
      id: "00000000-0000-4000-8000-000000000104",
      code: "U000104",
      username: "student04",
      fullName: "Học viên 04",
    };
    const addMock = vi
      .fn<ClassesApiDeps["addClassMembers"]>()
      .mockResolvedValueOnce({
        classId: BASE_CLASS.id,
        addedUserIds: [STUDENT_CANDIDATES[1]!.id],
        skippedUserIds: [unlistedMember.id],
        addedCount: 1,
        skippedCount: 1,
      })
      .mockRejectedValueOnce(new ClassesApiError(409, "inactive", "CLASS_INACTIVE"));

    const listUsersMock = vi.fn(async () => ({
      items: [...STUDENT_CANDIDATES, unlistedMember],
      page: 0,
      size: 20,
      totalItems: STUDENT_CANDIDATES.length + 1,
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

    await userEvent.click(within(dialog).getByRole("checkbox", { name: /student04/ }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /student03/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Thêm 2 học viên" }));

    await waitFor(() => {
      expect(addMock).toHaveBeenCalledWith(BASE_CLASS.id, {
        userIds: [unlistedMember.id, STUDENT_CANDIDATES[1]!.id],
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

  it("dialog thêm học viên đánh dấu 'Đã trong lớp' và khóa chọn học viên đã là thành viên đang hiển thị", async () => {
    renderDetail(makeDeps());

    await screen.findByRole("heading", { name: "Lớp A" });
    await screen.findByRole("link", { name: "student01" });
    await userEvent.click(screen.getByRole("button", { name: /Thêm học viên/ }));

    const dialog = screen.getByRole("dialog");
    const memberCheckbox = await within(dialog).findByRole("checkbox", { name: /student01/ });
    const candidateCheckbox = within(dialog).getByRole("checkbox", { name: /student03/ });

    expect(memberCheckbox).toBeDisabled();
    expect(candidateCheckbox).toBeEnabled();
    expect(within(dialog).getAllByText("Đã trong lớp")).toHaveLength(1);

    await userEvent.click(memberCheckbox);
    expect(within(dialog).getByText("Đã chọn 0 học viên")).toBeInTheDocument();

    await userEvent.click(candidateCheckbox);
    expect(within(dialog).getByRole("button", { name: "Thêm 1 học viên" })).toBeEnabled();
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

  it("AddMembersDialog phân trang ứng viên >20 và fallback STUDENT_ROLE_ID khi thiếu ROLE.VIEW", async () => {
    const user = userEvent.setup();
    const manyCandidates = Array.from({ length: 25 }, (_, i) => ({
      ...STUDENT_CANDIDATES[0]!,
      id: `cand-${i}`,
      username: `cand${String(i + 1).padStart(2, "0")}`,
      fullName: `Ứng viên ${i + 1}`,
    }));
    const listUsersMock = vi.fn(async (query: ListUsersQuery) => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      const start = page * size;
      return {
        items: manyCandidates.slice(start, start + size),
        page,
        size,
        totalItems: manyCandidates.length,
        totalPages: Math.ceil(manyCandidates.length / size),
      };
    });

    // Session thiếu ROLE.VIEW → không gọi listActiveRoles, fallback STUDENT_ROLE_ID
    const sessionNoRoleView: Session = {
      ...TEST_ADMIN_SESSION,
      permissions: TEST_ADMIN_SESSION.permissions.filter((p) => p !== "ROLE.VIEW"),
    };
    renderDetail(makeDeps(), sessionNoRoleView, listUsersMock);

    await screen.findByRole("heading", { name: "Lớp A" });
    await user.click(screen.getByRole("button", { name: /Thêm học viên/ }));

    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText("cand01");
    expect(within(dialog).queryByText("cand21")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenCalledWith(
        expect.objectContaining({ roleId: STUDENT_ROLE_ID, page: 0, size: 20 }),
      );
    });

    await user.click(within(dialog).getByRole("button", { name: "Sau" }));
    await within(dialog).findByText("cand21");
    expect(listUsersMock).toHaveBeenCalledWith(
      expect.objectContaining({ roleId: STUDENT_ROLE_ID, page: 1, size: 20 }),
    );
  });

  it("AddMembersDialog thiếu USER.VIEW → EmptyState không quyền, không gọi listUsers, ẩn nút submit", async () => {
    const user = userEvent.setup();
    const listUsersMock = vi.fn(async () => ({
      items: STUDENT_CANDIDATES,
      page: 0,
      size: 20,
      totalItems: STUDENT_CANDIDATES.length,
      totalPages: 1,
    }));
    const sessionNoUserView: Session = {
      ...TEST_ADMIN_SESSION,
      permissions: TEST_ADMIN_SESSION.permissions.filter((p) => p !== "USER.VIEW"),
    };
    renderDetail(makeDeps(), sessionNoUserView, listUsersMock);

    await screen.findByRole("heading", { name: "Lớp A" });
    await user.click(screen.getByRole("button", { name: /Thêm học viên/ }));

    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText("Không có quyền xem danh sách tài khoản");
    expect(listUsersMock).not.toHaveBeenCalled();
    expect(
      within(dialog).queryByRole("button", { name: /Thêm \d+ học viên/ }),
    ).not.toBeInTheDocument();
  });

  it("TransferMemberDialog lỗi tải danh sách lớp → báo lỗi, nút Thử lại refetch", async () => {
    const user = userEvent.setup();
    const listAllClassesMock = vi
      .fn<ClassesApiDeps["listAllClasses"]>()
      .mockRejectedValueOnce(new ClassesApiError(500, "boom"))
      .mockResolvedValueOnce([
        {
          id: "00000000-0000-4000-8000-000000000302",
          code: "LOP-B",
          name: "Lớp B",
          status: "ACTIVE",
          memberCount: 1,
          version: 0,
          createdAt: BASE_CLASS.createdAt,
          updatedAt: BASE_CLASS.updatedAt,
        } as ClassSummary,
      ]);

    renderDetail(makeDeps({ listAllClasses: listAllClassesMock }));

    await screen.findByText("student01");
    await user.click(screen.getByRole("button", { name: "Chuyển student01 sang lớp khác" }));

    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText("Không tải được danh sách lớp đích. Hãy thử lại.");

    await user.click(within(dialog).getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(listAllClassesMock).toHaveBeenCalledTimes(2);
    });
    // Sau retry thành công, select lớp đích được enable và có option LOP-B
    await waitFor(() => {
      expect(
        within(dialog).getByRole("combobox", { name: /Lớp đích/ }),
      ).not.toBeDisabled();
    });
  });

  it("Members badge fallback về memberCount khi đang tải, không hiện '0 học viên'; sau khi tải xong hiện đúng số", async () => {
    let resolveMembers!: (value: {
      items: ClassMember[];
      page: number;
      size: number;
      totalItems: number;
      totalPages: number;
    }) => void;
    const pendingMembers = new Promise<{
      items: ClassMember[];
      page: number;
      size: number;
      totalItems: number;
      totalPages: number;
    }>((resolve) => {
      resolveMembers = resolve;
    });
    const listClassMembersMock = vi
      .fn<ClassesApiDeps["listClassMembers"]>()
      .mockImplementationOnce(() => pendingMembers);

    renderDetail(makeDeps({ listClassMembers: listClassMembersMock }));

    await screen.findByRole("heading", { name: "Lớp A" });
    // membersQuery đang pending, không có search → badge fallback về memberCount=2,
    // không bao giờ hiển thị "0 học viên" (R1-8)
    expect(screen.queryByText(/0 học viên/)).not.toBeInTheDocument();
    expect(screen.getByText("2 học viên")).toBeInTheDocument();

    resolveMembers({
      items: SAMPLE_MEMBERS,
      page: 0,
      size: 20,
      totalItems: 2,
      totalPages: 1,
    });

    await screen.findByText("student01");
    expect(screen.getByText("2 học viên")).toBeInTheDocument();
  });

  it("Members lỗi tải → EmptyState 'Không tải được thành viên' + nút Thử lại refetch", async () => {
    const user = userEvent.setup();
    const listClassMembersMock = vi
      .fn<ClassesApiDeps["listClassMembers"]>()
      .mockRejectedValueOnce(new ClassesApiError(500, "boom"))
      .mockResolvedValueOnce({
        items: SAMPLE_MEMBERS,
        page: 0,
        size: 20,
        totalItems: 2,
        totalPages: 1,
      });

    renderDetail(makeDeps({ listClassMembers: listClassMembersMock }));

    await screen.findByRole("heading", { name: "Lớp A" });
    await screen.findByText("Không tải được thành viên");

    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("student01");
    expect(listClassMembersMock).toHaveBeenCalledTimes(2);
  });

  it("Members ở trang >1 rỗng → tự lùi về trang hợp lệ", async () => {
    let pageZeroCalls = 0;
    const listClassMembersMock = vi
      .fn<ClassesApiDeps["listClassMembers"]>()
      .mockImplementation(async (_id, query?: ListClassMembersQuery) => {
        const page = query?.page ?? 0;
        if (page === 1) {
          // Trang 2 (index 1) rỗng: dữ liệu đã giảm còn 1 trang → step-back về trang 0
          return { items: [], page: 1, size: 20, totalItems: 20, totalPages: 1 };
        }
        pageZeroCalls += 1;
        const items = Array.from({ length: 20 }, (_, i) => ({
          userId: `u-${i}`,
          userCode: `U${i}`,
          username: `member${String(i + 1).padStart(2, "0")}`,
          fullName: `Member ${i + 1}`,
          userStatus: "ACTIVE" as const,
          joinedAt: "2026-11-10T08:00:00Z",
        }));
        // Lần đầu (trang 1): 25 items / 2 trang để nút "Sau" enabled.
        // Sau step-back: 20 items / 1 trang.
        const first = pageZeroCalls === 1;
        return {
          items,
          page: 0,
          size: 20,
          totalItems: first ? 25 : 20,
          totalPages: first ? 2 : 1,
        };
      });

    renderDetail(makeDeps({ listClassMembers: listClassMembersMock }));

    await screen.findByText("member01");

    // Sang trang 2
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Sau" }));

    // Sau khi trang 2 trả về rỗng, effect step-back gọi lại trang 0
    await waitFor(() => {
      const calls = listClassMembersMock.mock.calls.map((c) => c[1]?.page);
      expect(calls).toContain(1);
      expect(calls.filter((p) => p === 0).length).toBeGreaterThanOrEqual(2);
    });
    await screen.findByText("member01");
  });

  it("Remove member ở trang cuối → step-back về trang trước và hiển thị member còn lại", async () => {
    const user = userEvent.setup();
    let memberList = [...SAMPLE_MEMBERS];
    const listClassMembersMock = vi
      .fn<ClassesApiDeps["listClassMembers"]>()
      .mockImplementation(async (_id, query?: ListClassMembersQuery) => {
        const page = query?.page ?? 0;
        const size = query?.size ?? 20;
        const items = memberList.slice(page * size, (page + 1) * size);
        return {
          items,
          page,
          size,
          totalItems: memberList.length,
          totalPages: Math.max(1, Math.ceil(memberList.length / size)),
        };
      });
    const removeMock = vi.fn(async () => {
      memberList = memberList.filter((m) => m.userId !== SAMPLE_MEMBERS[1]!.userId);
    });

    // 21 members → 2 trang; trang 2 có đúng 1 member
    memberList = [
      ...Array.from({ length: 20 }, (_, i) => ({
        userId: `u-${i}`,
        userCode: `U${i}`,
        username: `member${String(i + 1).padStart(2, "0")}`,
        fullName: `Member ${i + 1}`,
        userStatus: "ACTIVE" as const,
        joinedAt: "2026-11-10T08:00:00Z",
      })),
      SAMPLE_MEMBERS[1]!,
    ];

    renderDetail(
      makeDeps({ listClassMembers: listClassMembersMock, removeClassMember: removeMock }),
    );

    await screen.findByText("member01");

    // Sang trang 2
    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText("student02");

    // Loại student02 khỏi lớp
    await user.click(screen.getByRole("button", { name: "Loại student02 khỏi lớp" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Loại khỏi lớp" }));

    await waitFor(() => {
      expect(removeMock).toHaveBeenCalled();
    });

    // Sau khi xóa, trang 2 rỗng → step-back về trang 1, hiển thị member01
    await waitFor(() => {
      const calls = listClassMembersMock.mock.calls.map((c) => c[1]?.page);
      expect(calls).toContain(0);
    });
    await screen.findByText("member01");
    expect(screen.queryByText("student02")).not.toBeInTheDocument();
  });

  it("Locale en: không render text tiếng Việt trên màn hình lớp học", async () => {
    const enClass: ClassDetail = { ...BASE_CLASS, name: "Class A", code: "CLS-A" };
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <ClassDetailView
          classId={enClass.id}
          deps={makeDeps({ getClass: vi.fn(async () => enClass) })}
          listActiveRolesForAdd={async () => ROLES}
          listUsersForAdd={async () => ({
            items: STUDENT_CANDIDATES,
            page: 0,
            size: 20,
            totalItems: STUDENT_CANDIDATES.length,
            totalPages: 1,
          })}
        />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    expect(await screen.findByRole("heading", { name: "Class A" })).toBeInTheDocument();
    expect(screen.getByText("Add students")).toBeInTheDocument();
    expect(screen.getByText("Back to classes")).toBeInTheDocument();
    // Không có chuỗi UI tiếng Việt nào (fixture dữ liệu lớp/học viên không tính)
    const bodyText = document.body.textContent ?? "";
    for (const viText of [
      "Chi tiết lớp học",
      "Quay lại danh sách",
      "Thêm học viên",
      "Sửa thông tin",
      "Ngừng sử dụng",
      "Xóa lớp",
      "Thành viên",
      "học viên",
    ]) {
      expect(bodyText).not.toContain(viText);
    }
  });
});
