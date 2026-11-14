import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import {
  ClassesApiError,
  type ClassesApiDeps,
  type ClassSummary,
  defaultClassesApiDeps,
} from "../api";
import { ClassesListView } from "../components/classes-list-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

const SAMPLE_CLASSES: ClassSummary[] = [
  {
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
  },
  {
    id: "00000000-0000-4000-8000-000000000302",
    code: "LOP-2025",
    name: "Lớp khóa 2025",
    description: "Đã kết thúc",
    status: "INACTIVE",
    memberCount: 0,
    version: 1,
    createdAt: "2026-11-01T08:00:00Z",
    updatedAt: "2026-11-05T08:00:00Z",
  },
];

function makeDeps(overrides: Partial<ClassesApiDeps> = {}): Partial<ClassesApiDeps> {
  return {
    ...defaultClassesApiDeps,
    listClasses: vi.fn<ClassesApiDeps["listClasses"]>(async () => ({
      items: SAMPLE_CLASSES,
      page: 0,
      size: 20,
      totalItems: SAMPLE_CLASSES.length,
      totalPages: 1,
    })),
    createClass: vi.fn<ClassesApiDeps["createClass"]>(async (body) => ({
      id: "00000000-0000-4000-8000-000000000399",
      code: body.code,
      name: body.name,
      description: body.description,
      ownerUserId: body.ownerUserId,
      status: body.status ?? "ACTIVE",
      memberCount: 0,
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    })),
    updateClass: vi.fn<ClassesApiDeps["updateClass"]>(async (id, body) => ({
      id,
      code: body.code ?? "LOP-A",
      name: body.name ?? "Lớp A",
      description: body.description,
      status: "ACTIVE",
      memberCount: 2,
      version: body.version + 1,
      createdAt: "2026-11-10T08:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    })),
    updateClassStatus: vi.fn<ClassesApiDeps["updateClassStatus"]>(async (id, body) => ({
      id,
      code: "LOP-A",
      name: "Lớp A",
      status: body.status,
      memberCount: 2,
      version: body.version + 1,
      createdAt: "2026-11-10T08:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    })),
    deleteClass: vi.fn<ClassesApiDeps["deleteClass"]>(async () => undefined),
    ...overrides,
  };
}

function renderListView(
  deps: Partial<ClassesApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <ClassesListView
        deps={deps}
        listActiveUsers={async () => [
          {
            id: "00000000-0000-4000-8000-000000000002",
            code: "U000002",
            username: "ops.admin",
            fullName: "Vận hành CSKH",
            source: "ADMIN_CREATED",
            status: "ACTIVE",
            mustChangePassword: false,
            roles: [],
            version: 0,
            createdAt: "2026-11-01T00:00:00Z",
            updatedAt: "2026-11-01T00:00:00Z",
          },
        ]}
      />
    </SessionProvider>,
  );
}

afterEach(() => {
  routerMock.push.mockReset();
  vi.restoreAllMocks();
});

describe("ClassesListView", () => {
  it("chặn trang khi thiếu quyền CLASS.VIEW", async () => {
    const deps = makeDeps();
    renderListView(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });

    expect(await screen.findByText("Bạn không có quyền vào trang này")).toBeInTheDocument();
    expect(deps.listClasses).not.toHaveBeenCalled();
  });

  it("trạng thái đang tải không hiển thị 0 lớp", async () => {
    renderListView(makeDeps({ listClasses: vi.fn(() => new Promise<never>(() => {})) }));

    expect(await screen.findByText("Đang tải danh sách lớp học...")).toBeInTheDocument();
    expect(screen.queryByText("0 lớp")).not.toBeInTheDocument();
  });

  it("trạng thái lỗi hiện nút Thử lại và nạp lại danh sách", async () => {
    const listMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network"))
      .mockResolvedValueOnce({
        items: SAMPLE_CLASSES,
        page: 0,
        size: 20,
        totalItems: 2,
        totalPages: 1,
      });

    renderListView(makeDeps({ listClasses: listMock }));

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    expect(screen.getAllByText("Không tải được danh sách").length).toBeGreaterThan(0);

    await userEvent.click(retry);
    expect(await screen.findByText("LOP-A")).toBeInTheDocument();
    expect(screen.getByText("LOP-2025")).toBeInTheDocument();
  });

  it("trạng thái rỗng và lọc theo tìm kiếm + trạng thái", async () => {
    const listMock = vi.fn(async () => ({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    }));

    renderListView(makeDeps({ listClasses: listMock }));

    expect(await screen.findByText("Chưa có lớp học")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Tìm kiếm"), "LOP-X");
    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "LOP-X", page: 0, size: 20 }),
      );
    });
    expect(screen.getByText("Không tìm thấy lớp học")).toBeInTheDocument();

    const statusTrigger = screen.getByRole("combobox", { name: "Trạng thái" });
    await userEvent.click(statusTrigger);
    await userEvent.click(await screen.findByRole("option", { name: "Ngừng sử dụng" }));

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "LOP-X", status: "INACTIVE" }),
      );
    });
  });

  it("người dùng chỉ có CLASS.VIEW thấy danh sách và nút xem, không thấy nút Tạo/Sửa/Thao tác ghi", async () => {
    renderListView(makeDeps(), { ...TEST_ADMIN_SESSION, permissions: ["CLASS.VIEW"] });

    expect(await screen.findByText("LOP-A")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mở lớp LOP-A" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tạo lớp/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sửa lớp LOP-A" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thao tác khác với lớp/ })).not.toBeInTheDocument();
  });

  it("dialog tạo lớp kiểm tra quy tắc mã, gọi API tạo và báo lỗi CLASS_CODE_TAKEN theo mã lỗi", async () => {
    const createMock = vi
      .fn<ClassesApiDeps["createClass"]>()
      .mockRejectedValueOnce(new ClassesApiError(409, "taken", "CLASS_CODE_TAKEN"))
      .mockResolvedValueOnce({
        id: "cls-new",
        code: "LOP-C",
        name: "Lớp C",
        status: "ACTIVE",
        memberCount: 0,
        version: 0,
        createdAt: "2026-11-14T00:00:00Z",
        updatedAt: "2026-11-14T00:00:00Z",
      });

    renderListView(makeDeps({ createClass: createMock }));

    await screen.findByText("LOP-A");
    await userEvent.click(screen.getByRole("button", { name: /Tạo lớp/ }));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/2–30 ký tự chữ không dấu/)).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/Mã lớp/), "LOP A!");
    await userEvent.type(within(dialog).getByLabelText(/Tên lớp/), "Lớp C");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo lớp" }));
    expect(
      await within(dialog).findByText(/Mã lớp phải gồm 2–30 ký tự/),
    ).toBeInTheDocument();

    const codeInput = within(dialog).getByLabelText(/Mã lớp/);
    await userEvent.clear(codeInput);
    await userEvent.type(codeInput, "LOP-C");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo lớp" }));

    expect(await within(dialog).findByText("Mã lớp học đã tồn tại.")).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo lớp" }));
    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(2));
  });

  it("dialog ngừng sử dụng và xóa lớp bắt buộc nhập lý do", async () => {
    const updateStatusMock = vi.fn(async (id: string, body) => ({
      id,
      code: "LOP-A",
      name: "Lớp A",
      status: body.status,
      memberCount: 2,
      version: body.version + 1,
      createdAt: "2026-11-10T08:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    }));
    const deleteMock = vi.fn(async () => undefined);

    renderListView(
      makeDeps({ updateClassStatus: updateStatusMock, deleteClass: deleteMock }),
    );

    await screen.findByText("LOP-A");
    await userEvent.click(screen.getByRole("button", { name: "Thao tác khác với lớp LOP-A" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Ngừng sử dụng" }));

    const deactivateDialog = screen.getByRole("dialog");
    await userEvent.type(within(deactivateDialog).getByLabelText(/Lý do ngừng sử dụng/), "Hết khóa");
    await userEvent.click(within(deactivateDialog).getByRole("button", { name: "Ngừng sử dụng" }));

    await waitFor(() => {
      expect(updateStatusMock).toHaveBeenCalledWith(SAMPLE_CLASSES[0]!.id, {
        status: "INACTIVE",
        reason: "Hết khóa",
        version: 0,
      });
    });

    await userEvent.click(screen.getByRole("button", { name: "Thao tác khác với lớp LOP-A" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Xóa lớp" }));

    const deleteDialog = screen.getByRole("dialog");
    await userEvent.type(within(deleteDialog).getByLabelText(/Lý do xóa lớp/), "Tạo nhầm");
    await userEvent.click(within(deleteDialog).getByRole("button", { name: "Xóa lớp" }));

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith(SAMPLE_CLASSES[0]!.id, "Tạo nhầm");
    });
  });

  it("đang ở trang >1 và trang rỗng → tự lùi về trang cuối hợp lệ", async () => {
    let pageZeroCalls = 0;
    const listMock = vi.fn<ClassesApiDeps["listClasses"]>(async (query) => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      if (page === 1) {
        // Trang 2 (index 1) rỗng: dữ liệu đã giảm còn 1 trang → step-back về trang 0
        return { items: [], page: 1, size, totalItems: 20, totalPages: 1 };
      }
      pageZeroCalls += 1;
      const items = Array.from({ length: 20 }, (_, i) => ({
        ...SAMPLE_CLASSES[0]!,
        id: `cls-${i}`,
        code: `LOP-${String(i + 1).padStart(2, "0")}`,
        name: `Lớp ${i + 1}`,
      }));
      const first = pageZeroCalls === 1;
      return {
        items,
        page: 0,
        size,
        totalItems: first ? 25 : 20,
        totalPages: first ? 2 : 1,
      };
    });

    renderListView(makeDeps({ listClasses: listMock }));

    await screen.findByText("LOP-01");

    // Sang trang 2
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Sau" }));

    // Sau khi trang 2 trả về rỗng, effect step-back gọi lại trang 0
    await waitFor(() => {
      const calls = listMock.mock.calls.map((c) => c[0]?.page);
      expect(calls).toContain(1);
      expect(calls.filter((p) => p === 0).length).toBeGreaterThanOrEqual(2);
    });
    await screen.findByText("LOP-01");
  });

  it("Xóa lớp ở trang cuối → step-back và hiển thị lại danh sách trang trước", async () => {
    const user = userEvent.setup();
    const page2Class: ClassSummary = {
      ...SAMPLE_CLASSES[0]!,
      id: "cls-last",
      code: "LOP-LAST",
      name: "Lớp cuối",
    };
    let deleted = false;
    const listMockWithPage2 = vi.fn<ClassesApiDeps["listClasses"]>(async (query) => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      if (page === 1) {
        // Trang 2 có 1 lớp; sau khi xóa → rỗng, totalItems giảm còn 20
        if (deleted) {
          return { items: [], page: 1, size, totalItems: 20, totalPages: 1 };
        }
        return { items: [page2Class], page: 1, size, totalItems: 21, totalPages: 2 };
      }
      const items = Array.from({ length: 20 }, (_, i) => ({
        ...SAMPLE_CLASSES[0]!,
        id: `cls-${i}`,
        code: `LOP-${String(i + 1).padStart(2, "0")}`,
        name: `Lớp ${i + 1}`,
      }));
      return { items, page: 0, size, totalItems: deleted ? 20 : 21, totalPages: deleted ? 1 : 2 };
    });

    const deleteMock = vi.fn(async () => {
      deleted = true;
    });

    renderListView(
      makeDeps({ listClasses: listMockWithPage2, deleteClass: deleteMock }),
    );

    await screen.findByText("LOP-01");

    // Sang trang 2
    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText("LOP-LAST");

    // Xóa LOP-LAST
    await user.click(screen.getByRole("button", { name: "Thao tác khác với lớp LOP-LAST" }));
    await user.click(await screen.findByRole("menuitem", { name: "Xóa lớp" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Lý do xóa lớp/), "Dọn lớp");
    await user.click(within(dialog).getByRole("button", { name: "Xóa lớp" }));

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalled();
    });

    // Sau khi xóa, trang 2 rỗng → step-back về trang 1, hiển thị LOP-01
    await waitFor(() => {
      const calls = listMockWithPage2.mock.calls.map((c) => c[0]?.page);
      expect(calls.filter((p) => p === 0).length).toBeGreaterThanOrEqual(2);
    });
    await screen.findByText("LOP-01");
    expect(screen.queryByText("LOP-LAST")).not.toBeInTheDocument();
  });

  it("Locale en: ClassesListView hiển thị tiếng Anh, không có text tiếng Việt", async () => {
    const enClasses: ClassSummary[] = [
      {
        ...SAMPLE_CLASSES[0]!,
        name: "Class A",
        code: "CLS-A",
      },
    ];
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <ClassesListView
          deps={makeDeps({
            listClasses: vi.fn(async () => ({
              items: enClasses,
              page: 0,
              size: 20,
              totalItems: 1,
              totalPages: 1,
            })),
          })}
          listActiveUsers={async () => []}
        />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    expect(await screen.findByText("CLS-A")).toBeInTheDocument();
    expect(screen.getByText("Classes")).toBeInTheDocument();
    expect(screen.getByText("Create class")).toBeInTheDocument();
    const bodyText = document.body.textContent ?? "";
    for (const viText of ["Lớp học", "Tạo lớp", "Thành viên", "học viên"]) {
      expect(bodyText).not.toContain(viText);
    }
  });
});
