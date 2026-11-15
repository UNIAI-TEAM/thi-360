import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
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
    expect(screen.getAllByText("Không tải được danh sách")).toHaveLength(1);
    expect(screen.getByText("Chưa xác định số lớp")).toBeInTheDocument();

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
    // C-05: kết quả tìm kiếm được đọc qua vùng status.
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Không có lớp học nào"));

    const statusTrigger = screen.getByRole("combobox", { name: "Trạng thái" });
    await userEvent.click(statusTrigger);
    await userEvent.click(await screen.findByRole("option", { name: "Ngừng sử dụng" }));

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "LOP-X", status: "INACTIVE" }),
      );
    });
  });

  it("đọc số lớp tìm được qua một vùng status luôn có mặt, để rỗng trong lúc đang tải lại (C-05)", async () => {
    type ClassesPage = Awaited<ReturnType<ClassesApiDeps["listClasses"]>>;
    const first = Promise.withResolvers<ClassesPage>();
    const second = Promise.withResolvers<ClassesPage>();
    const listMock = vi
      .fn<ClassesApiDeps["listClasses"]>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    renderListView(makeDeps({ listClasses: listMock }));

    // Vùng status có mặt từ lúc bảng còn đang tải (chưa có chữ), không dựng lên cùng lúc với kết quả.
    await screen.findByRole("table", { name: "Danh sách lớp học" });
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("");
    expect(status).toHaveClass("sr-only");
    first.resolve({
      items: SAMPLE_CLASSES,
      page: 0,
      size: 20,
      totalItems: SAMPLE_CLASSES.length,
      totalPages: 1,
    });
    await waitFor(() =>
      expect(status).toHaveTextContent(`Có ${SAMPLE_CLASSES.length} lớp học`),
    );

    await userEvent.type(screen.getByLabelText("Tìm kiếm"), "LOP-A");
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
    // Đang tải lại: bảng giữ dòng cũ, vùng status để rỗng chờ kết quả mới.
    expect(status).toHaveTextContent("");

    second.resolve({
      items: [SAMPLE_CLASSES[0]!],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    await waitFor(() => expect(status).toHaveTextContent("Có 1 lớp học"));
    expect(screen.getAllByRole("status")).toHaveLength(1);
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
    // C-21: nút "⋯" là IconButton của kit, mang vòng focus chung.
    expect(screen.getByRole("button", { name: "Thao tác khác với lớp LOP-A" })).toHaveClass(
      "focus-visible:ring-3",
      "focus-visible:ring-primary-text",
    );
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

describe("ClassesListView: dùng được từ 360px (UNI-2586)", () => {
  it("cột ưu tiên thấp được đánh dấu để ẩn theo khung chứa bảng; Tên lớp và Trạng thái luôn là cột, không còn dòng tóm tắt", async () => {
    renderListView(makeDeps());
    const table = await screen.findByRole("table", { name: "Danh sách lớp học" });
    await within(table).findByText("LOP-A");

    const container = table.closest(".\\@container")!;
    expect(container).not.toBeNull();
    for (const rule of [
      "@max-[64rem]:[&_:is(th,td):has([data-col=description])]:hidden",
      "@max-[56rem]:[&_:is(th,td):has([data-col=owner])]:hidden",
      "@max-3xl:[&_:is(th,td):has([data-col=memberCount])]:hidden",
    ]) {
      expect(container.className).toContain(rule);
    }
    expect(container.className).not.toContain("@max-[40rem]");
    for (const col of ["description", "owner", "memberCount"]) {
      expect(table.querySelector(`thead [data-col='${col}']`)).not.toBeNull();
      expect(table.querySelectorAll(`tbody [data-col='${col}']`)).toHaveLength(SAMPLE_CLASSES.length);
    }
    expect(table.querySelector("[data-col-summary]")).toBeNull();

    const link = within(table).getByRole("link", { name: "LOP-A" });
    expect(link).toHaveAttribute("href", "/classes/00000000-0000-4000-8000-000000000301");
    expect(link).not.toHaveClass("after:absolute");

    // Tên lớp xuống tối đa hai dòng, không cắt một dòng và không dựa vào title (DESIGN.md 5.13).
    const nameCell = within(table).getByText("Lớp A");
    expect(nameCell).toHaveClass("line-clamp-2");
    expect(nameCell).not.toHaveClass("truncate");
    expect(nameCell).not.toHaveAttribute("title");
  });

  it("thanh lọc chia theo bề rộng thẻ lọc như danh sách tài khoản", async () => {
    renderListView(makeDeps());
    await screen.findByText("LOP-A");

    const form = screen.getByRole("form", { name: "Bộ lọc lớp học" });
    expect(form.parentElement).toHaveClass("@container");
    expect(form).toHaveClass("grid", "@3xl:flex");
    expect(screen.getByLabelText("Tìm kiếm").closest(".col-span-full")).not.toBeNull();
  });
});

describe("ClassesListView: khung hẹp thành danh sách thẻ (UNI-2587)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("khung 358px: mỗi lớp một thẻ: mã lớp dẫn tới chi tiết, tên lớp, chip trạng thái, số học viên, người phụ trách", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderListView(makeDeps());

    const list = await screen.findByRole("list", { name: "Danh sách lớp học" });
    await within(list).findByText("LOP-A");
    expect(screen.queryByRole("table")).toBeNull();
    const [first, second] = within(list).getAllByRole("listitem");

    const link = within(first!).getByRole("link", { name: "LOP-A" });
    expect(link).toHaveAttribute("href", "/classes/00000000-0000-4000-8000-000000000301");
    expect(link).toHaveClass("after:absolute", "after:inset-0");
    expect(within(first!).getByText("Lớp A")).toHaveClass("text-caption");
    expect(within(first!).getByText("Hoạt động").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(first).toHaveTextContent("Học viên: 2 · Người phụ trách: ops.admin");

    expect(within(second!).getByText("Ngừng sử dụng").closest("[data-tone]")).toHaveAttribute("data-tone", "neutral");
    expect(second).toHaveTextContent("Học viên: 0 · Người phụ trách: —");
  });

  it("thẻ có nút Sửa và menu ⋯ như cột Thao tác (không có nút Xem vì cả thẻ là liên kết), nằm trên vùng bấm", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderListView(makeDeps());
    const list = await screen.findByRole("list", { name: "Danh sách lớp học" });
    await within(list).findByText("LOP-A");
    const [first] = within(list).getAllByRole("listitem");

    expect(within(first!).queryByRole("button", { name: "Mở lớp LOP-A" })).toBeNull();
    const edit = within(first!).getByRole("button", { name: "Sửa lớp LOP-A" });
    expect(edit.closest(".z-1")).toHaveClass("relative");
    await userEvent.click(edit);
    expect(await screen.findByRole("dialog", { name: "Sửa thông tin lớp" })).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    await userEvent.click(within(first!).getByRole("button", { name: "Thao tác khác với lớp LOP-A" }));
    expect(await screen.findByRole("menuitem", { name: "Ngừng sử dụng" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Xóa lớp" })).toBeInTheDocument();
  });

  it("chỉ có CLASS.VIEW: thẻ không có nút thao tác", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderListView(makeDeps(), { ...TEST_ADMIN_SESSION, permissions: ["CLASS.VIEW"] });
    const list = await screen.findByRole("list", { name: "Danh sách lớp học" });
    await within(list).findByText("LOP-A");
    expect(within(list).queryByRole("button")).toBeNull();
    expect(within(list).getByRole("link", { name: "LOP-A" })).toBeInTheDocument();
  });

  it("ô Sắp xếp theo: Mã lớp, Tên lớp, Trạng thái; chọn thì gửi sắp xếp lên máy chủ và về trang 1", async () => {
    const listClassesMock = vi.fn<ClassesApiDeps["listClasses"]>(async () => ({
      items: SAMPLE_CLASSES,
      page: 0,
      size: 20,
      totalItems: SAMPLE_CLASSES.length,
      totalPages: 1,
    }));
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderListView(makeDeps({ listClasses: listClassesMock }));
    await screen.findByText("LOP-A");

    await userEvent.click(screen.getByRole("combobox", { name: "Sắp xếp theo" }));
    const options = (await screen.findAllByRole("option")).map((option) => option.textContent);
    expect(options).toEqual([
      "Mã lớp · tăng dần",
      "Mã lớp · giảm dần",
      "Tên lớp · tăng dần",
      "Tên lớp · giảm dần",
      "Trạng thái · tăng dần",
      "Trạng thái · giảm dần",
    ]);
    await userEvent.click(screen.getByRole("option", { name: "Tên lớp · tăng dần" }));
    await waitFor(() =>
      expect(listClassesMock).toHaveBeenLastCalledWith(expect.objectContaining({ sort: "name,asc", page: 0 })),
    );
  });

  it("khung rộng: vẫn là bảng với nút Xem, Sửa và menu ⋯ ở cột Thao tác", async () => {
    installContainerWidth(1100);
    renderListView(makeDeps());
    const table = await screen.findByRole("table", { name: "Danh sách lớp học" });
    await within(table).findByText("LOP-A");
    expect(screen.queryByRole("list", { name: "Danh sách lớp học" })).toBeNull();
    expect(within(table).getByRole("button", { name: "Mở lớp LOP-A" })).toBeInTheDocument();
    expect(within(table).getByRole("button", { name: "Sửa lớp LOP-A" })).toBeInTheDocument();
    expect(within(table).getByRole("button", { name: "Thao tác khác với lớp LOP-A" })).toBeInTheDocument();
  });
});
