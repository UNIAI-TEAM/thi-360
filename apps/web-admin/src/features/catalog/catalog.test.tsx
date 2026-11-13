import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { useMemo, useSyncExternalStore } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { ApiStatusError } from "@/lib/api/query-client";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  CatalogApiError,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type PageResponseCatalogGroupResponse,
  type PageResponseCatalogItemResponse,
} from "./api";
import { CatalogView } from "./components/catalog-view";
import { CATALOG_ERROR_CODES, pickName, resolveCatalogErrorMessage } from "./utils";

const routerMock = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const state = {
    pathname: "/cau-hinh-chung",
    search: "",
    replace: vi.fn((url: string) => {
      const idx = url.indexOf("?");
      state.search = idx >= 0 ? url.slice(idx + 1) : "";
      for (const listener of listeners) listener();
    }),
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    getSnapshot() {
      return state.search;
    },
  };
  return state;
});

vi.mock("next/navigation", () => ({
  usePathname: () => routerMock.pathname,
  useSearchParams: () => {
    const search = useSyncExternalStore(
      routerMock.subscribe,
      routerMock.getSnapshot,
      routerMock.getSnapshot,
    );
    return useMemo(() => new URLSearchParams(search), [search]);
  },
  useRouter: () => ({
    replace: routerMock.replace,
    push: routerMock.replace,
  }),
}));

const SYSTEM_SUBJECT_GROUP: CatalogGroupResponse = {
  id: "grp-subject",
  code: "SUBJECT",
  nameVi: "Môn học",
  nameEn: "Subject",
  description: "Danh mục môn học",
  status: "ACTIVE",
  type: "SYSTEM",
  system: true,
  kind: "SIMPLE",
  refGroups: [],
  itemCount: 3,
  createdAt: "2026-11-14T00:00:00Z",
  updatedAt: "2026-11-14T00:00:00Z",
  version: 0,
};

const SYSTEM_FORMAT_GROUP: CatalogGroupResponse = {
  id: "grp-format",
  code: "QUESTION_FORMAT",
  nameVi: "Dạng câu",
  nameEn: "Question Format",
  status: "ACTIVE",
  type: "SYSTEM",
  system: true,
  kind: "SIMPLE",
  refGroups: [],
  itemCount: 1,
  createdAt: "2026-11-14T00:00:00Z",
  updatedAt: "2026-11-14T00:00:00Z",
  version: 0,
};

const SYSTEM_TASK_TYPE_GROUP: CatalogGroupResponse = {
  id: "grp-task-type",
  code: "TASK_TYPE",
  nameVi: "Dạng bài",
  nameEn: "Task Type",
  status: "ACTIVE",
  type: "SYSTEM",
  system: true,
  kind: "SIMPLE",
  refGroups: [],
  itemCount: 0,
  createdAt: "2026-11-14T00:00:00Z",
  updatedAt: "2026-11-14T00:00:00Z",
  version: 0,
};

const CUSTOM_SOURCE_GROUP: CatalogGroupResponse = {
  id: "grp-source",
  code: "QUESTION_SOURCE",
  nameVi: "Nguồn câu hỏi",
  nameEn: "Question Source",
  status: "ACTIVE",
  type: "CUSTOM",
  system: false,
  kind: "SIMPLE",
  refGroups: [],
  itemCount: 0,
  createdAt: "2026-11-14T00:00:00Z",
  updatedAt: "2026-11-14T00:00:00Z",
  version: 1,
};

const SUBJECT_ITEMS: CatalogItemResponse[] = [
  {
    id: "item-toan",
    groupId: "grp-subject",
    groupCode: "SUBJECT",
    code: "TOAN",
    value: 1,
    nameVi: "Toán học",
    nameEn: "Mathematics",
    displayOrder: 0,
    status: "ACTIVE",
    effectiveStatus: "ACTIVE",
    system: false,
    depth: 1,
    path: "/item-toan/",
    childCount: 0,
    ownRefs: [],
    inheritedRefs: [],
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
    version: 0,
  },
  {
    id: "item-van",
    groupId: "grp-subject",
    groupCode: "SUBJECT",
    code: "VAN",
    value: 2,
    nameVi: "Ngữ văn",
    nameEn: "Literature",
    displayOrder: 1,
    status: "ACTIVE",
    effectiveStatus: "ACTIVE",
    system: false,
    depth: 1,
    path: "/item-van/",
    childCount: 0,
    ownRefs: [],
    inheritedRefs: [],
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
    version: 0,
  },
  {
    id: "item-dia",
    groupId: "grp-subject",
    groupCode: "SUBJECT",
    code: "DIA",
    value: 3,
    nameVi: "Địa lí",
    displayOrder: 2,
    status: "INACTIVE",
    effectiveStatus: "INACTIVE",
    system: false,
    depth: 1,
    path: "/item-dia/",
    childCount: 0,
    ownRefs: [],
    inheritedRefs: [],
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
    version: 0,
  },
];

function pageOfGroups(items: CatalogGroupResponse[]): PageResponseCatalogGroupResponse {
  return {
    items,
    page: 0,
    size: 100,
    totalItems: items.length,
    totalPages: 1,
  };
}

function pageOfItems(items: CatalogItemResponse[]): PageResponseCatalogItemResponse {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: 1,
  };
}

function createMockApi(overrides: Partial<CatalogApi> = {}): CatalogApi {
  const base: CatalogApi = {
    listGroups: vi.fn(async () =>
      pageOfGroups([
        SYSTEM_SUBJECT_GROUP,
        SYSTEM_FORMAT_GROUP,
        SYSTEM_TASK_TYPE_GROUP,
        CUSTOM_SOURCE_GROUP,
      ]),
    ),
    getGroup: vi.fn(async () => SYSTEM_SUBJECT_GROUP),
    createGroup: vi.fn(async (payload) => ({
      ...CUSTOM_SOURCE_GROUP,
      id: "grp-new",
      code: payload.code,
      nameVi: payload.nameVi,
      nameEn: payload.nameEn,
      status: payload.status ?? "ACTIVE",
      description: payload.description,
    })),
    updateGroup: vi.fn(async (id, payload) => ({
      ...CUSTOM_SOURCE_GROUP,
      id,
      code: payload.code ?? CUSTOM_SOURCE_GROUP.code,
      nameVi: payload.nameVi ?? CUSTOM_SOURCE_GROUP.nameVi,
      nameEn: payload.nameEn,
      status: payload.status ?? CUSTOM_SOURCE_GROUP.status,
      description: payload.description,
      version: CUSTOM_SOURCE_GROUP.version + 1,
    })),
    deleteGroup: vi.fn(async () => undefined),
    listItems: vi.fn(async (groupId, query) => {
      const source = groupId === "grp-subject" ? SUBJECT_ITEMS : [];
      const q = query?.q?.trim().toLowerCase() ?? "";
      const filtered = source.filter((item) => {
        const matchesStatus = !query?.status || item.status === query.status;
        const matchesQ =
          !q ||
          item.code.toLowerCase().includes(q) ||
          item.nameVi.toLowerCase().includes(q) ||
          (item.nameEn ?? "").toLowerCase().includes(q);
        return matchesStatus && matchesQ;
      });
      return pageOfItems(filtered);
    }),
    createItem: vi.fn(async (groupId, payload) => ({
      id: "item-new",
      groupId,
      groupCode: "SUBJECT",
      code: payload.code,
      value: 4,
      nameVi: payload.nameVi,
      nameEn: payload.nameEn,
      description: payload.description,
      displayOrder: payload.displayOrder ?? 3,
      status: payload.status ?? "ACTIVE",
      effectiveStatus: payload.status ?? "ACTIVE",
      system: false,
      depth: 1,
      path: "/item-new/",
      childCount: 0,
      ownRefs: [],
      inheritedRefs: [],
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
      version: 0,
    })),
    updateItem: vi.fn(async (itemId, payload) => ({
      ...SUBJECT_ITEMS[0]!,
      id: itemId,
      code: payload.code ?? SUBJECT_ITEMS[0]!.code,
      nameVi: payload.nameVi ?? SUBJECT_ITEMS[0]!.nameVi,
      nameEn: payload.nameEn,
      status: payload.status ?? SUBJECT_ITEMS[0]!.status,
      version: SUBJECT_ITEMS[0]!.version + 1,
    })),
    deleteItem: vi.fn(async () => undefined),
    reorderItems: vi.fn<CatalogApi["reorderItems"]>(async (_groupId, payload) =>
      payload.itemIds
        .map((id, index) => {
          const found = SUBJECT_ITEMS.find((i) => i.id === id);
          return found ? { ...found, displayOrder: index } : null;
        })
        .filter((x): x is CatalogItemResponse => x !== null),
    ),
  };
  for (const [key, value] of Object.entries(overrides)) {
    if (value !== undefined) {
      Object.assign(base, { [key]: value });
    }
  }
  return base;
}


function renderCatalog({
  permissions = TEST_ADMIN_SESSION.permissions,
  api = createMockApi(),
  locale = "vi",
  search = "",
}: {
  permissions?: readonly string[];
  api?: CatalogApi;
  locale?: "vi" | "en";
  search?: string;
} = {}) {
  routerMock.search = search;
  const activeMessages = locale === "en" ? en : viMessages;
  return {
    api,
    ...renderWithProviders(
      <>
        <SessionProvider
          loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions: [...permissions] })}
        >
          <CatalogView api={api} />
        </SessionProvider>
        <Toaster
          containerAriaLabel={activeMessages.common.notificationsLabel}
          closeButtonAriaLabel={activeMessages.common.closeToast}
        />
      </>,
      { locale, messages: activeMessages },
    ),
  };
}

beforeEach(() => {
  routerMock.search = "";
  routerMock.replace.mockClear();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("pickName", () => {
  it("trả nameVi ở locale vi, trả nameEn ở locale en và dự phòng nameVi khi nameEn trống", () => {
    expect(pickName({ nameVi: "Toán học", nameEn: "Mathematics" }, "vi")).toBe("Toán học");
    expect(pickName({ nameVi: "Toán học", nameEn: "Mathematics" }, "en")).toBe("Mathematics");
    expect(pickName({ nameVi: "Địa lí", nameEn: "   " }, "en")).toBe("Địa lí");
    expect(pickName({ nameVi: "Địa lí", nameEn: undefined }, "en-US")).toBe("Địa lí");
  });
});

describe("error-code mapping", () => {
  it("ánh xạ đầy đủ mã lỗi danh mục sang khóa dịch tiếng Việt và tiếng Anh", () => {
    for (const code of CATALOG_ERROR_CODES) {
      const viText = resolveCatalogErrorMessage(new CatalogApiError(409, "", code), (key) => {
        return viMessages.catalog.errorCodes[key];
      });
      const enText = resolveCatalogErrorMessage(new CatalogApiError(409, "", code), (key) => {
        return en.catalog.errorCodes[key];
      });
      expect(viText).toBe(viMessages.catalog.errorCodes[code]);
      expect(enText).toBe(en.catalog.errorCodes[code]);
      expect(viText).not.toEqual(enText);
    }
    expect(
      resolveCatalogErrorMessage(
        new ApiStatusError(412),
        (key) => en.catalog.errorCodes[key],
      ),
    ).toBe(en.catalog.errorCodes.VERSION_CONFLICT);
  });
});

describe("CatalogView: phân quyền và bốn trạng thái", () => {
  it("chặn truy cập khi thiếu quyền CATALOG.VIEW", async () => {
    renderCatalog({ permissions: ["USER.VIEW"] });
    expect(
      await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" }),
    ).toBeInTheDocument();
  });

  it("người chỉ có CATALOG.VIEW không thấy bất kỳ nút ghi nào", async () => {
    renderCatalog({ permissions: ["CATALOG.VIEW"], search: "group=SUBJECT" });

    expect(await screen.findByRole("heading", { name: "Môn học" })).toBeInTheDocument();
    expect(await screen.findByText("Toán học")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thêm danh mục" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thêm giá trị" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thao tác danh mục" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa Toán học/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Xóa Toán học/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Kéo để đổi thứ tự/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Di chuyển Toán học/ })).not.toBeInTheDocument();
  });

  it("hiện trạng thái đang tải, lỗi (Thử lại), chưa chọn nhóm, và rỗng giá trị", async () => {
    const deferred = Promise.withResolvers<PageResponseCatalogGroupResponse>();
    const loadingApi = createMockApi({
      listGroups: vi.fn(() => deferred.promise),
    });
    const { unmount } = renderCatalog({ api: loadingApi });
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    unmount();

    const retryGroups = vi
      .fn<CatalogApi["listGroups"]>()
      .mockRejectedValueOnce(new ApiStatusError(500))
      .mockResolvedValueOnce(
        pageOfGroups([SYSTEM_SUBJECT_GROUP, CUSTOM_SOURCE_GROUP]),
      );
    const api = createMockApi({ listGroups: retryGroups });
    renderCatalog({ api });

    const retryBtn = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retryBtn);

    expect(await screen.findByRole("heading", { name: "Chưa chọn danh mục" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thêm giá trị" })).not.toBeInTheDocument();

    const sidebar = screen.getByRole("heading", { name: /Danh mục/ }).closest("section")!;
    expect(within(sidebar).getByText("Hệ thống · 1")).toBeInTheDocument();
    expect(within(sidebar).getByText("Tùy chỉnh · 1")).toBeInTheDocument();

    await userEvent.click(within(sidebar).getByRole("button", { name: /Nguồn câu hỏi/ }));
    expect(routerMock.replace).toHaveBeenCalledWith(
      "/cau-hinh-chung?group=QUESTION_SOURCE",
      { scroll: false },
    );
    expect(await screen.findByRole("heading", { name: "Chưa có giá trị" })).toBeInTheDocument();
  });

  it("menu '…' chỉ hiện trên danh mục Tùy chỉnh và ẩn trên danh mục Hệ thống", async () => {
    renderCatalog({ search: "group=SUBJECT" });
    expect(await screen.findByRole("heading", { name: "Môn học" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thao tác danh mục" })).not.toBeInTheDocument();
    expect(screen.getByText("Hệ thống", { selector: "span" })).toBeInTheDocument();

    const sidebar = screen.getByRole("heading", { name: /Danh mục/ }).closest("section")!;
    await userEvent.click(within(sidebar).getByRole("button", { name: /Nguồn câu hỏi/ }));
    expect(await screen.findByRole("button", { name: "Thao tác danh mục" })).toBeInTheDocument();
  });
});

describe("CatalogView: bảng giá trị, tìm kiếm, lọc và sắp xếp thứ tự", () => {
  it("tìm kiếm + lọc trạng thái khóa kéo thả/sắp xếp, đổi thứ tự lạc quan và hoàn tác khi lỗi", async () => {
    const reorderMock = vi
      .fn<CatalogApi["reorderItems"]>()
      .mockRejectedValueOnce(new CatalogApiError(500, "fail", "INTERNAL_ERROR"))
      .mockResolvedValueOnce([SUBJECT_ITEMS[1]!, SUBJECT_ITEMS[0]!, SUBJECT_ITEMS[2]!]);

    const api = createMockApi({ reorderItems: reorderMock });
    renderCatalog({ api, search: "group=SUBJECT" });

    const table = await screen.findByRole("table", { name: "Danh sách giá trị danh mục" });
    expect(await within(table).findByText("Toán học")).toBeInTheDocument();
    // Khi lọc theo từ khóa, nút đổi thứ tự bị khóa
    const searchInput = screen.getByRole("searchbox", { name: "Tìm theo mã hoặc tên" });
    await userEvent.type(searchInput, "Toán");
    await waitFor(() => {
      expect(within(table).queryByText("Ngữ văn")).not.toBeInTheDocument();
    });
    const disabledDrag = within(table).getByRole("button", {
      name: "Kéo để đổi thứ tự Toán học",
    });
    expect(disabledDrag).toHaveAttribute("aria-disabled", "true");

    // Xóa bộ lọc: đổi thứ tự mở lại
    await userEvent.clear(searchInput);
    await waitFor(() => {
      expect(within(table).getByText("Ngữ văn")).toBeInTheDocument();
    });

    const getRowOrder = () =>
      within(table)
        .getAllByRole("row")
        .slice(1)
        .map((row) => within(row).getAllByRole("cell")[1]!.textContent);

    expect(getRowOrder()).toEqual(["TOAN", "VAN", "DIA"]);

    // Lần 1: lỗi -> tự hoàn tác về thứ tự cũ và hiện thông báo lỗi
    const moveDownToan = within(table).getByRole("button", {
      name: "Di chuyển Toán học xuống",
    });
    await userEvent.click(moveDownToan);

    await waitFor(() => {
      expect(reorderMock).toHaveBeenCalledWith("grp-subject", {
        itemIds: ["item-van", "item-toan", "item-dia"],
      });
    });
    await waitFor(() => {
      expect(getRowOrder()).toEqual(["TOAN", "VAN", "DIA"]);
    });
    expect(
      await screen.findByText("Không lưu được thứ tự mới. Danh sách đã được hoàn tác."),
    ).toBeInTheDocument();

    // Lần 2: thành công -> giữ thứ tự mới và có toast Hoàn tác
    await userEvent.click(
      within(table).getByRole("button", { name: "Di chuyển Toán học xuống" }),
    );
    await waitFor(() => {
      expect(getRowOrder()).toEqual(["VAN", "TOAN", "DIA"]);
    });
    expect(await screen.findByText("Đã cập nhật thứ tự hiển thị.")).toBeInTheDocument();
  });
});

describe("CatalogView: dialog nhóm và dialog giá trị", () => {
  it("dialog thêm/sửa nhóm khóa ô mã khi API báo CATALOG_CODE_LOCKED và giữ nguyên dữ liệu đã nhập", async () => {
    const updateGroupMock = vi
      .fn<CatalogApi["updateGroup"]>()
      .mockRejectedValueOnce(new CatalogApiError(409, "locked", "CATALOG_CODE_LOCKED"));
    const api = createMockApi({ updateGroup: updateGroupMock });
    renderCatalog({ api, search: "group=QUESTION_SOURCE" });

    const menuTrigger = await screen.findByRole("button", { name: "Thao tác danh mục" });
    await userEvent.click(menuTrigger);
    await userEvent.click(await screen.findByRole("menuitem", { name: "Sửa danh mục" }));

    const dialog = await screen.findByRole("dialog");
    const codeInput = within(dialog).getByLabelText(/Mã danh mục/);
    const nameViInput = within(dialog).getByLabelText(/Tên tiếng Việt/);

    await userEvent.clear(codeInput);
    await userEvent.type(codeInput, "NEW_SOURCE");
    await userEvent.clear(nameViInput);
    await userEvent.type(nameViInput, "Nguồn câu hỏi mới");

    await userEvent.click(within(dialog).getByRole("button", { name: "Lưu" }));

    expect(
      await within(dialog).findByText(viMessages.catalog.errorCodes.CATALOG_CODE_LOCKED),
    ).toBeInTheDocument();
    expect(codeInput).toBeDisabled();
    expect(codeInput).toHaveValue("QUESTION_SOURCE");
    expect(nameViInput).toHaveValue("Nguồn câu hỏi mới");
  });

  it("dialog giá trị: không có ô value, nameVi bắt buộc, nameEn không bắt buộc, cảnh báo QUESTION_FORMAT/TASK_TYPE và khóa mã khi CATALOG_CODE_LOCKED", async () => {
    const updateItemMock = vi
      .fn<CatalogApi["updateItem"]>()
      .mockRejectedValueOnce(new CatalogApiError(409, "locked", "CATALOG_CODE_LOCKED"));
    const api = createMockApi({ updateItem: updateItemMock });

    renderCatalog({ api, search: "group=QUESTION_FORMAT" });

    // Mở dialog thêm giá trị cho QUESTION_FORMAT -> hiện dòng cảnh báo
    await userEvent.click(await screen.findByRole("button", { name: "Thêm giá trị" }));
    const createDialog = await screen.findByRole("dialog");
    expect(
      within(createDialog).getByText("Thêm giá trị không tự tạo chức năng xử lý tương ứng."),
    ).toBeInTheDocument();
    expect(within(createDialog).queryByLabelText(/^Value$/i)).not.toBeInTheDocument();

    // Kiểm tra validation nameVi bắt buộc, nameEn không bắt buộc
    await userEvent.type(within(createDialog).getByLabelText(/Mã giá trị/), "ESSAY");
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));
    expect(
      await within(createDialog).findByText(viMessages.catalog.validation.nameViRequired),
    ).toBeInTheDocument();

    await userEvent.type(within(createDialog).getByLabelText(/Tên tiếng Việt/), "Tự luận");
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));
    await waitFor(() => {
      expect(api.createItem).toHaveBeenCalledWith("grp-format", {
        code: "ESSAY",
        nameVi: "Tự luận",
        status: "ACTIVE",
      });
    });

    // Chuyển sang SUBJECT -> không hiện cảnh báo, kiểm tra CATALOG_CODE_LOCKED khi sửa
    const sidebar = screen.getByRole("heading", { name: /Danh mục/ }).closest("section")!;
    await userEvent.click(within(sidebar).getByRole("button", { name: /Môn học/ }));

    const editBtn = await screen.findByRole("button", { name: "Sửa Toán học" });
    await userEvent.click(editBtn);

    const editDialog = await screen.findByRole("dialog");
    expect(
      within(editDialog).queryByText("Thêm giá trị không tự tạo chức năng xử lý tương ứng."),
    ).not.toBeInTheDocument();

    const itemCodeInput = within(editDialog).getByLabelText(/Mã giá trị/);
    const itemNameEnInput = within(editDialog).getByLabelText(/Tên tiếng Anh/);
    await userEvent.clear(itemCodeInput);
    await userEvent.type(itemCodeInput, "MATH_NEW");
    await userEvent.clear(itemNameEnInput);
    await userEvent.type(itemNameEnInput, "Maths");

    await userEvent.click(within(editDialog).getByRole("button", { name: "Lưu" }));
    expect(
      await within(editDialog).findByText(viMessages.catalog.errorCodes.CATALOG_CODE_LOCKED),
    ).toBeInTheDocument();
    expect(itemCodeInput).toBeDisabled();
    expect(itemCodeInput).toHaveValue("TOAN");
    expect(itemNameEnInput).toHaveValue("Maths");
  });

  it("chuyển sang TASK_TYPE vẫn hiện cảnh báo chức năng xử lý tương ứng", async () => {
    renderCatalog({ search: "group=TASK_TYPE" });
    await userEvent.click(await screen.findByRole("button", { name: "Thêm giá trị" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("Thêm giá trị không tự tạo chức năng xử lý tương ứng."),
    ).toBeInTheDocument();
  });

  it("tạo mới nhóm tùy chỉnh, tìm kiếm trong cột nhóm và xóa nhóm rỗng kèm lý do", async () => {
    const api = createMockApi();
    renderCatalog({ api });

    await userEvent.click(await screen.findByRole("button", { name: "Thêm danh mục" }));
    const createDialog = await screen.findByRole("dialog");

    await userEvent.type(within(createDialog).getByLabelText(/Mã danh mục/), "TOPIC");
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));
    expect(
      await within(createDialog).findByText(viMessages.catalog.validation.nameViRequired),
    ).toBeInTheDocument();

    await userEvent.type(within(createDialog).getByLabelText(/Tên tiếng Việt/), "Chủ đề");
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));
    await waitFor(() => {
      expect(api.createGroup).toHaveBeenCalledWith({
        code: "TOPIC",
        nameVi: "Chủ đề",
        status: "ACTIVE",
        kind: "SIMPLE",
      });
    });

    // Tìm kiếm trong cột nhóm
    const searchGroups = screen.getByRole("searchbox", { name: "Tìm danh mục" });
    await userEvent.type(searchGroups, "QUESTION_SOURCE");
    const sidebar = screen.getByRole("heading", { name: /Danh mục/ }).closest("section")!;
    expect(within(sidebar).getByRole("button", { name: /Nguồn câu hỏi/ })).toBeInTheDocument();
    expect(within(sidebar).queryByRole("button", { name: /Môn học/ })).not.toBeInTheDocument();

    await userEvent.click(within(sidebar).getByRole("button", { name: /Nguồn câu hỏi/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Thao tác danh mục" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Xóa danh mục" }));

    const confirmDialog = await screen.findByRole("dialog");
    const confirmBtn = within(confirmDialog).getByRole("button", { name: "Xóa danh mục" });
    expect(confirmBtn).toBeDisabled();

    await userEvent.type(within(confirmDialog).getByLabelText(/Lý do xóa/), "Không còn dùng");
    expect(confirmBtn).not.toBeDisabled();
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(api.deleteGroup).toHaveBeenCalledWith("grp-source", "Không còn dùng");
    });
  });

  it("hiển thị đúng tiếng Anh và dùng pickName dự phòng nameVi khi locale là en", async () => {
    renderCatalog({ locale: "en", search: "group=SUBJECT" });
    expect(await screen.findByRole("heading", { name: "Subject" })).toBeInTheDocument();
    expect(await screen.findByText("Toán học")).toBeInTheDocument();
    expect(screen.getByText("Mathematics")).toBeInTheDocument();
    const sidebar = screen.getByRole("heading", { name: /Catalogs/ }).closest("section")!;
    expect(within(sidebar).getByRole("button", { name: /Subject/ })).toBeInTheDocument();
    expect(within(sidebar).getByText(/System ·/)).toBeInTheDocument();
    expect(within(sidebar).getByText(/Custom ·/)).toBeInTheDocument();
  });
});
