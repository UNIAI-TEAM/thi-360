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
  type CatalogTreeNodeResponse,
  type CatalogOptionResponse,
  type CombinationVersionResponse,
  type PageResponseCatalogGroupResponse,
  type PageResponseCatalogItemResponse,
} from "./api";
import { CatalogView } from "./components/catalog-view";
import {
  CATALOG_ERROR_CODES,
  pickName,
  resolveCatalogErrorMessage,
  resolveSystemTreeLevelKey,
  resolveTreeLevelLabel,
} from "./utils";

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
const SYSTEM_COMBINATION_GROUP: CatalogGroupResponse = {
  id: "grp-combination",
  code: "SUBJECT_COMBINATION",
  nameVi: "Mã tổ hợp",
  nameEn: "Subject Combination",
  status: "ACTIVE",
  type: "SYSTEM",
  system: true,
  kind: "COMBINATION",
  refGroups: [],
  itemCount: 1,
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

const COMBINATION_VERSIONS: CombinationVersionResponse[] = [
  {
    id: "comb-v1",
    itemId: "item-qd01",
    itemCode: "QDA_QD01",
    itemNameVi: "QĐ01",
    itemNameEn: "QD01",
    versionNo: 1,
    examItemId: "exam-qda",
    examCode: "QDA",
    examNameVi: "Đánh giá năng lực Bộ Quốc phòng",
    examNameEn: "QDA Exam",
    examStatus: "ACTIVE",
    sourceNote: "[MẪU] Dữ liệu thử nghiệm",
    isCurrent: true,
    createdAt: "2026-11-14T00:00:00Z",
    subjects: [
      {
        subjectItemId: "item-toan",
        code: "TOAN",
        nameVi: "Toán học",
        nameEn: "Mathematics",
        position: 0,
        status: "ACTIVE",
      },
      {
        subjectItemId: "item-van",
        code: "VAN",
        nameVi: "Ngữ văn",
        nameEn: "Literature",
        position: 1,
        status: "ACTIVE",
      },
    ],
  },
];

const COMBINATION_ITEMS: CatalogItemResponse[] = [
  {
    id: "item-qd01",
    groupId: "grp-combination",
    groupCode: "SUBJECT_COMBINATION",
    code: "QDA_QD01",
    value: 1,
    nameVi: "QĐ01",
    nameEn: "QD01",
    displayOrder: 0,
    status: "ACTIVE",
    effectiveStatus: "ACTIVE",
    system: false,
    depth: 1,
    path: "/item-qd01/",
    childCount: 0,
    ownRefs: [],
    inheritedRefs: [],
    combination: COMBINATION_VERSIONS[0],
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
    version: 0,
  },
];

const EXAM_OPTIONS: CatalogOptionResponse[] = [
  { id: "exam-qda", code: "QDA", nameVi: "Đánh giá năng lực Bộ Quốc phòng", nameEn: "QDA Exam", value: 1, displayOrder: 0 },
  { id: "exam-hsa", code: "HSA", nameVi: "Đánh giá năng lực ĐHQG Hà Nội", nameEn: "HSA Exam", value: 2, displayOrder: 1 },
];

const SUBJECT_OPTIONS: CatalogOptionResponse[] = [
  { id: "item-toan", code: "TOAN", nameVi: "Toán học", nameEn: "Mathematics", value: 1, displayOrder: 0 },
  { id: "item-van", code: "VAN", nameVi: "Ngữ văn", nameEn: "Literature", value: 2, displayOrder: 1 },
  { id: "item-anh", code: "ANH", nameVi: "Tiếng Anh", nameEn: "English", value: 3, displayOrder: 2 },
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
        SYSTEM_COMBINATION_GROUP,
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
      const source =
        groupId === "grp-subject"
          ? SUBJECT_ITEMS
          : groupId === "grp-combination"
            ? COMBINATION_ITEMS
            : [];
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
    getOptions: vi.fn(async (groupCode) => {
      if (groupCode === "EXAM") return EXAM_OPTIONS;
      if (groupCode === "SUBJECT") return SUBJECT_OPTIONS;
      return [];
    }),
    getItem: vi.fn(async (itemId) => ({
      ...SUBJECT_ITEMS[0]!,
      id: itemId,
    })),
    listTree: vi.fn(async () => []),
    moveItem: vi.fn(async (itemId, payload) => ({
      ...SUBJECT_ITEMS[0]!,
      id: itemId,
      parentId: payload.parentId,
    })),
    listRefGroups: vi.fn(async () => []),
    addRefGroup: vi.fn(async () => []),
    removeRefGroup: vi.fn(async () => undefined),
    addItemRef: vi.fn(async (itemId) => ({
      ...SUBJECT_ITEMS[0]!,
      id: itemId,
    })),
    removeItemRef: vi.fn(async () => undefined),
    getDescendants: vi.fn(async () => []),
    getItemUsages: vi.fn(async (itemId) => ({
      targetId: itemId,
      targetCode: itemId === "item-qd01" ? "QDA_QD01" : "TOAN",
      totalCount: 0,
      blocking: false,
      usages: [],
    })),
    listItemVersions: vi.fn(async () => COMBINATION_VERSIONS),
    getCombinationVersion: vi.fn(async () => COMBINATION_VERSIONS[0]!),
    listItemAuditEvents: vi.fn<CatalogApi["listItemAuditEvents"]>(async (itemId) => ({
      items: [
        {
          id: "audit-1",
          action: "CATALOG_ITEM_CREATED",
          actorType: "USER",
          actorUsername: "ops.admin",
          actorRoles: ["ADMIN"],
          module: "catalog",
          occurredAt: "2026-11-14T01:00:00Z",
          outcome: "SUCCESS",
          targetId: itemId,
          targetType: "CATALOG_ITEM",
        },
      ],
      page: 0,
      size: 10,
      totalItems: 1,
      totalPages: 1,
    })),
    exportItems: vi.fn(async (groupId) => ({
      blob: new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
      filename: `catalog-${groupId}.xlsx`,
    })),
    previewImport: vi.fn<CatalogApi["previewImport"]>(async (groupId) => ({
      previewId: "preview-1",
      groupId,
      groupCode: groupId === "grp-knowledge" ? "KNOWLEDGE" : "SUBJECT",
      groupKind: groupId === "grp-knowledge" ? "TREE" : "SIMPLE",
      totalRows: 2,
      validRows: 2,
      errorRows: 0,
      canConfirm: true,
      errors: [],
    })),
    confirmImport: vi.fn(async (groupId, previewId) => ({
      previewId,
      groupId,
      groupCode: groupId === "grp-knowledge" ? "KNOWLEDGE" : "SUBJECT",
      totalRows: 2,
      createdCount: 2,
      reusedCount: groupId === "grp-knowledge" ? 1 : 0,
    })),
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
    expect(pickName({ nameVi: "   ", nameEn: "Physics" }, "vi")).toBe("Physics");
  });
});

describe("resolveTreeLevelLabel", () => {
  it("dịch 3 cấp hệ thống (Chương, Bài, Kiến thức) theo vi/en và giữ nguyên cấp tùy chỉnh", () => {
    expect(resolveSystemTreeLevelKey("Chương")).toBe("chapter");
    expect(resolveSystemTreeLevelKey("Bài")).toBe("lesson");
    expect(resolveSystemTreeLevelKey("Kiến thức")).toBe("knowledge");
    expect(resolveSystemTreeLevelKey("Chủ đề")).toBeNull();

    const systemLevels = ["Chương", "Bài", "Kiến thức"];
    expect(
      systemLevels.map((lvl) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: true,
          translateLevel: (key) => viMessages.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Chương", "Bài", "Kiến thức"]);

    expect(
      systemLevels.map((lvl) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: true,
          translateLevel: (key) => en.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Chapter", "Lesson", "Knowledge"]);

    const customLevels = ["Khối", "Chủ đề", "Chương"];
    expect(
      customLevels.map((lvl) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: false,
          translateLevel: (key) => en.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Khối", "Chủ đề", "Chương"]);

    expect(
      resolveTreeLevelLabel(undefined, {
        isSystemGroup: true,
        translateLevel: (key) => en.catalog.tree.systemLevels[key],
        fallback: "Root",
      }),
    ).toBe("Root");
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
    expect(screen.queryByRole("button", { name: /Ngừng sử dụng / })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Kéo để đổi thứ tự/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Di chuyển Toán học/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chi tiết Toán học" })).toBeInTheDocument();
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
  }, 15_000);
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
});

describe("CatalogView Slice 2: tổ hợp, ngăn chi tiết, ngừng sử dụng và xóa đang dùng", () => {
  it("nhóm COMBINATION hiển thị thêm cột Kỳ thi, Các môn theo thứ tự và Phiên bản", async () => {
    renderCatalog({ search: "group=SUBJECT_COMBINATION" });

    const table = await screen.findByRole("table", { name: "Danh sách giá trị danh mục" });
    expect(within(table).getByRole("columnheader", { name: "Kỳ thi" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Các môn" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Phiên bản" })).toBeInTheDocument();

    expect(await within(table).findByText("QDA_QD01")).toBeInTheDocument();
    expect(within(table).getByText("QĐ01")).toBeInTheDocument();
    expect(within(table).getByText("Đánh giá năng lực Bộ Quốc phòng")).toBeInTheDocument();
    expect(within(table).getByText("Toán học")).toBeInTheDocument();
    expect(within(table).getByText("Ngữ văn")).toBeInTheDocument();
    expect(within(table).getByText("v1")).toBeInTheDocument();
  });

  it("dialog tổ hợp: tạo mới chọn kỳ thi, thêm & đổi thứ tự môn, nguồn quy ước; khi sửa tổ hợp đang dùng hiện cảnh báo phiên bản mới", async () => {
    const api = createMockApi({
      getItemUsages: vi.fn(async (itemId) => ({
        targetId: itemId,
        targetCode: "QDA_QD01",
        totalCount: 2,
        blocking: false,
        usages: [
          {
            source: "Đề thi",
            objectType: "EXAM_BLUEPRINT",
            count: 2,
            blocking: false,
            sampleLabels: ["Đề QĐ01 - 2026"],
          },
        ],
      })),
    });
    renderCatalog({ api, search: "group=SUBJECT_COMBINATION" });

    await userEvent.click(await screen.findByRole("button", { name: "Thêm giá trị" }));
    const createDialog = await screen.findByRole("dialog");

    await userEvent.type(within(createDialog).getByLabelText(/Mã giá trị/), "HSA_Q02");
    await userEvent.type(within(createDialog).getByLabelText(/Tên tiếng Việt/), "Q02");

    // Kiểm tra validate bắt buộc kỳ thi & môn
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));
    expect(
      await within(createDialog).findByText(viMessages.catalog.validation.examRequired),
    ).toBeInTheDocument();
    expect(
      within(createDialog).getByText(viMessages.catalog.validation.subjectsRequired),
    ).toBeInTheDocument();

    // Chọn kỳ thi
    await userEvent.click(within(createDialog).getByRole("combobox", { name: /Kỳ thi/ }));
    await userEvent.click(
      await screen.findByRole("option", { name: "HSA — Đánh giá năng lực ĐHQG Hà Nội" }),
    );

    // Thêm 2 môn rồi đổi thứ tự môn thứ 2 lên đầu
    await userEvent.click(within(createDialog).getByRole("combobox", { name: /Thêm môn/ }));
    await userEvent.click(await screen.findByRole("option", { name: "TOAN — Toán học" }));
    await userEvent.click(within(createDialog).getByRole("combobox", { name: /Thêm môn/ }));
    await userEvent.click(await screen.findByRole("option", { name: "ANH — Tiếng Anh" }));

    await userEvent.click(
      within(createDialog).getByRole("button", { name: "Di chuyển Tiếng Anh (ANH) lên" }),
    );
    await userEvent.type(within(createDialog).getByLabelText(/Nguồn quy ước/), "Quy ước mới");
    await userEvent.click(within(createDialog).getByRole("button", { name: "Lưu" }));

    await waitFor(() => {
      expect(api.createItem).toHaveBeenCalledWith("grp-combination", {
        code: "HSA_Q02",
        nameVi: "Q02",
        status: "ACTIVE",
        examItemId: "exam-hsa",
        subjectItemIds: ["item-anh", "item-toan"],
        sourceNote: "Quy ước mới",
      });
    });

    // Mở dialog sửa QDA_QD01: đổi thứ tự môn khi tổ hợp đang được dùng -> hiện cảnh báo phiên bản 2
    await userEvent.click(await screen.findByRole("button", { name: "Sửa QĐ01" }));
    const editDialog = await screen.findByRole("dialog");
    expect(
      await within(editDialog).findByText(/Phiên bản hiện tại: 1/),
    ).toBeInTheDocument();

    await userEvent.click(
      await within(editDialog).findByRole("button", { name: "Di chuyển Ngữ văn (VAN) lên" }),
    );
    expect(
      await within(editDialog).findByText(/sẽ tạo phiên bản 2/),
    ).toBeInTheDocument();
    expect(
      within(editDialog).getByRole("button", { name: "Lưu thành phiên bản mới" }),
    ).toBeInTheDocument();
  });

  it("ngăn chi tiết giá trị: xem Thông tin, Nơi đang dùng, Lịch sử thay đổi (ẩn nếu thiếu AUDIT.VIEW) và Phiên bản cho tổ hợp", async () => {
    const historyVersions: CombinationVersionResponse[] = [
      {
        ...COMBINATION_VERSIONS[0]!,
        id: "comb-v2",
        versionNo: 2,
        isCurrent: true,
        sourceNote: "Bản cập nhật",
      },
      {
        ...COMBINATION_VERSIONS[0]!,
        id: "comb-v1",
        versionNo: 1,
        isCurrent: false,
      },
    ];
    const api = createMockApi({
      listItemVersions: vi.fn(async () => historyVersions),
      getItemUsages: vi.fn(async (itemId) => ({
        targetId: itemId,
        targetCode: "QDA_QD01",
        totalCount: 3,
        blocking: false,
        usages: [
          {
            source: "Mã tổ hợp",
            objectType: "SUBJECT_COMBINATION",
            count: 3,
            blocking: false,
            sampleLabels: ["QĐ01"],
          },
        ],
      })),
    });

    const { unmount } = renderCatalog({
      api,
      permissions: ["CATALOG.VIEW", "AUDIT.VIEW"],
      search: "group=SUBJECT_COMBINATION",
    });

    await userEvent.click(await screen.findByRole("button", { name: "Chi tiết QĐ01" }));
    const drawer = await screen.findByRole("dialog");
    expect(within(drawer).getByText("Cấu hình tổ hợp hiện hành")).toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole("tab", { name: "Nơi đang dùng" }));
    expect(await within(drawer).findByText("Tổng cộng 3 lượt tham chiếu")).toBeInTheDocument();
    expect(within(drawer).getByText("Mã tổ hợp")).toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole("tab", { name: "Lịch sử thay đổi" }));
    expect(await within(drawer).findByText("Tạo mới giá trị")).toBeInTheDocument();
    expect(within(drawer).getByText("ops.admin")).toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole("tab", { name: "Phiên bản" }));
    expect(await within(drawer).findByText("Phiên bản 2")).toBeInTheDocument();
    expect(within(drawer).getByText("Phiên bản 1")).toBeInTheDocument();
    expect(within(drawer).getByText("Hiện hành")).toBeInTheDocument();
    unmount();

    // Nhóm SIMPLE không có tab Phiên bản, và thiếu AUDIT.VIEW sẽ hiện thông báo quyền thay vì gọi API audit
    const listAuditMock = vi.fn();
    renderCatalog({
      api: createMockApi({ listItemAuditEvents: listAuditMock }),
      permissions: ["CATALOG.VIEW"],
      search: "group=SUBJECT",
    });
    await userEvent.click(await screen.findByRole("button", { name: "Chi tiết Toán học" }));
    const simpleDrawer = await screen.findByRole("dialog");
    expect(within(simpleDrawer).queryByRole("tab", { name: "Phiên bản" })).not.toBeInTheDocument();
    await userEvent.click(within(simpleDrawer).getByRole("tab", { name: "Lịch sử thay đổi" }));
    expect(
      await within(simpleDrawer).findByText(viMessages.catalog.detailDrawer.auditPermissionDenied),
    ).toBeInTheDocument();
    expect(listAuditMock).not.toHaveBeenCalled();
  });

  it("luồng ngừng sử dụng gọi usages trước, khóa khi blocking và gửi lý do khi hợp lệ", async () => {
    const blockedApi = createMockApi({
      getItemUsages: vi.fn(async () => ({
        targetId: "item-toan",
        targetCode: "TOAN",
        totalCount: 1,
        blocking: true,
        usages: [
          {
            source: "Mã tổ hợp",
            objectType: "SUBJECT_COMBINATION",
            count: 1,
            blocking: true,
            sampleLabels: ["QĐ01"],
          },
        ],
      })),
    });
    const { unmount } = renderCatalog({ api: blockedApi, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Ngừng sử dụng Toán học" }));
    const blockedDialog = await screen.findByRole("dialog");
    expect(
      await within(blockedDialog).findByText(viMessages.catalog.stopItemDialog.blockedAlert),
    ).toBeInTheDocument();
    expect(
      within(blockedDialog).getByRole("button", { name: "Ngừng sử dụng" }),
    ).toBeDisabled();
    unmount();

    const allowedApi = createMockApi({
      getItemUsages: vi.fn(async () => ({
        targetId: "item-toan",
        targetCode: "TOAN",
        totalCount: 0,
        blocking: false,
        usages: [],
      })),
    });
    renderCatalog({ api: allowedApi, search: "group=SUBJECT" });
    await userEvent.click(await screen.findByRole("button", { name: "Ngừng sử dụng Toán học" }));
    const stopDialog = await screen.findByRole("dialog");

    const confirmStopBtn = await within(stopDialog).findByRole("button", { name: "Ngừng sử dụng" });
    await waitFor(() => expect(confirmStopBtn).not.toBeDisabled());
    await userEvent.click(confirmStopBtn);
    expect(
      await within(stopDialog).findByText(viMessages.catalog.validation.reasonRequired),
    ).toBeInTheDocument();

    await userEvent.type(within(stopDialog).getByLabelText(/Lý do ngừng sử dụng/), "Tạm ẩn");
    await userEvent.click(confirmStopBtn);

    await waitFor(() => {
      expect(allowedApi.updateItem).toHaveBeenCalledWith("item-toan", {
        status: "INACTIVE",
        reason: "Tạm ẩn",
        version: 0,
      });
    });
  });

  it("xóa giá trị đang dùng (CATALOG_ITEM_IN_USE) hiện nơi đang dùng và gợi ý chuyển sang ngừng sử dụng", async () => {
    const api = createMockApi({
      deleteItem: vi.fn(async () => {
        throw new CatalogApiError(409, "In use", "CATALOG_ITEM_IN_USE", [
          { field: "id", code: "CATALOG_ITEM_IN_USE", message: "Mã tổ hợp (1): QĐ01" },
        ]);
      }),
      getItemUsages: vi.fn(async () => ({
        targetId: "item-toan",
        targetCode: "TOAN",
        totalCount: 1,
        blocking: false,
        usages: [
          {
            source: "Mã tổ hợp",
            objectType: "SUBJECT_COMBINATION",
            count: 1,
            blocking: false,
            sampleLabels: ["QĐ01"],
          },
        ],
      })),
    });
    renderCatalog({ api, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Xóa Toán học" }));
    const deleteConfirmDialog = await screen.findByRole("dialog");
    await userEvent.type(within(deleteConfirmDialog).getByLabelText(/Lý do xóa/), "Không dùng nữa");
    await userEvent.click(within(deleteConfirmDialog).getByRole("button", { name: "Xóa giá trị" }));

    expect(
      await screen.findByRole("heading", { name: "Không thể xóa giá trị đang được dùng" }),
    ).toBeInTheDocument();
    const inUseDialog = screen.getByRole("dialog");
    expect(await within(inUseDialog).findByText("Mã tổ hợp")).toBeInTheDocument();
    expect(within(inUseDialog).getByText("QĐ01")).toBeInTheDocument();

    await userEvent.click(
      within(inUseDialog).getByRole("button", { name: "Chuyển sang ngừng sử dụng" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Ngừng sử dụng giá trị?" }),
    ).toBeInTheDocument();
  });

  it("hiển thị đúng tiếng Anh và dùng pickName dự phòng nameVi khi locale là en", async () => {
    renderCatalog({ locale: "en", search: "group=SUBJECT" });
    expect(await screen.findByRole("heading", { name: "Subject" })).toBeInTheDocument();
    expect(await screen.findByText("Toán học")).toBeInTheDocument();
    expect(screen.getByText("Mathematics")).toBeInTheDocument();
    const sidebar = screen.getByRole("heading", { name: /Catalogs/ }).closest("section")!;
    expect(within(sidebar).getByRole("button", { name: /^Subject\s*SUBJECT\s*\d+$/ })).toBeInTheDocument();
    expect(within(sidebar).getByText(/System ·/)).toBeInTheDocument();
    expect(within(sidebar).getByText(/Custom ·/)).toBeInTheDocument();
  });
});

const SYSTEM_KNOWLEDGE_GROUP: CatalogGroupResponse = {
  id: "grp-knowledge",
  code: "KNOWLEDGE",
  nameVi: "Cấu trúc kiến thức",
  nameEn: "Knowledge taxonomy",
  description: "Cây kiến thức theo chương, bài, đơn vị kiến thức",
  kind: "TREE",
  type: "SYSTEM",
  system: true,
  status: "ACTIVE",
  itemCount: 3,
  levels: ["Chương", "Bài", "Kiến thức"],
  refGroups: [
    {
      refGroupId: "grp-subject",
      code: "SUBJECT",
      nameVi: "Môn học",
      nameEn: "Subject",
      status: "ACTIVE",
      position: 0,
      isDefault: true,
      linkedItemCount: 2,
    },
  ],
  createdAt: "2026-11-14T00:00:00Z",
  updatedAt: "2026-11-14T00:00:00Z",
  version: 0,
};

const KNOWLEDGE_CH1: CatalogTreeNodeResponse = {
  id: "node-ch1",
  groupId: "grp-knowledge",
  code: "CH1",
  nameVi: "Hàm số",
  nameEn: "Functions",
  description: "Chương hàm số",
  displayOrder: 0,
  status: "ACTIVE",
  effectiveStatus: "ACTIVE",
  depth: 1,
  path: "/node-ch1/",
  hasChildren: true,
  childCount: 1,
  matched: false,
  ownRefs: [
    {
      refItemId: "item-toan",
      code: "TOAN",
      nameVi: "Toán học",
      nameEn: "Mathematics",
      status: "ACTIVE",
      refGroupId: "grp-subject",
      refGroupCode: "SUBJECT",
      refGroupNameVi: "Môn học",
      refGroupNameEn: "Subject",
    },
  ],
  inheritedRefs: [],
  value: 1,
  version: 0,
};

const KNOWLEDGE_CH2: CatalogTreeNodeResponse = {
  id: "node-ch2",
  groupId: "grp-knowledge",
  code: "CH2",
  nameVi: "Tích phân",
  nameEn: "Integrals",
  displayOrder: 1,
  status: "ACTIVE",
  effectiveStatus: "ACTIVE",
  depth: 1,
  path: "/node-ch2/",
  hasChildren: false,
  childCount: 0,
  matched: false,
  ownRefs: [],
  inheritedRefs: [],
  value: 2,
  version: 0,
};

const KNOWLEDGE_BAI1: CatalogTreeNodeResponse = {
  id: "node-b1",
  groupId: "grp-knowledge",
  parentId: "node-ch1",
  code: "B1",
  nameVi: "Tính đơn điệu",
  nameEn: "Monotonicity",
  displayOrder: 0,
  status: "ACTIVE",
  effectiveStatus: "INACTIVE",
  depth: 2,
  path: "/node-ch1/node-b1/",
  hasChildren: true,
  childCount: 1,
  matched: false,
  ownRefs: [
    {
      refItemId: "item-van",
      code: "VAN",
      nameVi: "Ngữ văn",
      nameEn: "Literature",
      status: "ACTIVE",
      refGroupId: "grp-subject",
      refGroupCode: "SUBJECT",
      refGroupNameVi: "Môn học",
      refGroupNameEn: "Subject",
    },
  ],
  inheritedRefs: [
    {
      refItemId: "item-toan",
      code: "TOAN",
      nameVi: "Toán học",
      nameEn: "Mathematics",
      status: "ACTIVE",
      refGroupId: "grp-subject",
      refGroupCode: "SUBJECT",
      refGroupNameVi: "Môn học",
      refGroupNameEn: "Subject",
      fromItemId: "node-ch1",
      fromItemCode: "CH1",
      fromItemNameVi: "Hàm số",
      fromItemNameEn: "Functions",
    },
  ],
  value: 3,
  version: 1,
};

const KNOWLEDGE_KT1: CatalogTreeNodeResponse = {
  id: "node-kt1",
  groupId: "grp-knowledge",
  parentId: "node-b1",
  code: "KT1",
  nameVi: "Cực trị",
  nameEn: "Extrema",
  displayOrder: 0,
  status: "ACTIVE",
  effectiveStatus: "ACTIVE",
  depth: 3,
  path: "/node-ch1/node-b1/node-kt1/",
  hasChildren: false,
  childCount: 0,
  matched: false,
  ownRefs: [],
  inheritedRefs: KNOWLEDGE_BAI1.inheritedRefs,
  value: 4,
  version: 0,
};

function toTreeItemDetail(node: CatalogTreeNodeResponse): CatalogItemResponse {
  return {
    id: node.id,
    groupId: node.groupId,
    groupCode: "KNOWLEDGE",
    parentId: node.parentId,
    code: node.code,
    nameVi: node.nameVi,
    nameEn: node.nameEn,
    description: node.description,
    displayOrder: node.displayOrder,
    status: node.status,
    effectiveStatus: node.effectiveStatus,
    depth: node.depth,
    path: node.path,
    childCount: node.childCount,
    ownRefs: node.ownRefs,
    inheritedRefs: node.inheritedRefs,
    value: node.value,
    version: node.version,
    system: false,
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
  };
}

function createTreeMockApi(overrides: Partial<CatalogApi> = {}): CatalogApi {
  const nodes = [KNOWLEDGE_CH1, KNOWLEDGE_CH2, KNOWLEDGE_BAI1, KNOWLEDGE_KT1];
  return createMockApi({
    listGroups: vi.fn(async () =>
      pageOfGroups([
        SYSTEM_SUBJECT_GROUP,
        SYSTEM_FORMAT_GROUP,
        SYSTEM_TASK_TYPE_GROUP,
        SYSTEM_COMBINATION_GROUP,
        SYSTEM_KNOWLEDGE_GROUP,
        CUSTOM_SOURCE_GROUP,
      ]),
    ),
    listTree: vi.fn<CatalogApi["listTree"]>(async (_groupId, query) => {
      if (query?.q) {
        return [KNOWLEDGE_CH1, { ...KNOWLEDGE_BAI1, matched: true }];
      }
      if (query?.ref && query.ref.length > 0) {
        return [{ ...KNOWLEDGE_CH1, matched: true }];
      }
      if (query?.depth === 1) {
        return [KNOWLEDGE_CH1, KNOWLEDGE_CH2];
      }
      if (query?.parentId === "node-ch1") return [KNOWLEDGE_BAI1];
      if (query?.parentId === "node-b1") return [KNOWLEDGE_KT1];
      if (query?.parentId) return [];
      return [KNOWLEDGE_CH1, KNOWLEDGE_CH2];
    }),
    getItem: vi.fn(async (itemId) => {
      const found = nodes.find((n) => n.id === itemId) ?? KNOWLEDGE_CH1;
      return toTreeItemDetail(found);
    }),
    getDescendants: vi.fn(async () => [
      {
        id: "node-b1",
        code: "B1",
        nameVi: "Tính đơn điệu",
        nameEn: "Monotonicity",
        depth: 2,
        parentId: "node-ch1",
        path: "/node-ch1/node-b1/",
        value: 3,
      },
      {
        id: "node-kt1",
        code: "KT1",
        nameVi: "Cực trị",
        nameEn: "Extrema",
        depth: 3,
        parentId: "node-b1",
        path: "/node-ch1/node-b1/node-kt1/",
        value: 4,
      },
    ]),
    reorderItems: vi.fn<CatalogApi["reorderItems"]>(async (_groupId, payload) =>
      payload.itemIds.map((id, idx) => {
        const found = nodes.find((n) => n.id === id) ?? KNOWLEDGE_CH1;
        return { ...toTreeItemDetail(found), displayOrder: idx };
      }),
    ),
    moveItem: vi.fn(async (itemId, payload) => ({
      ...toTreeItemDetail(KNOWLEDGE_BAI1),
      id: itemId,
      parentId: payload.parentId,
      path: `/${payload.parentId}/${itemId}/`,
      version: 2,
    })),
    ...overrides,
  });
}

describe("CatalogView Slice 3: danh mục cây nhiều cấp, liên kết và chuyển nhánh", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("tạo danh mục kiểu TREE với các cấp và gắn danh mục liên kết SIMPLE", async () => {
    const api = createTreeMockApi();
    renderCatalog({ api, search: "group=KNOWLEDGE" });

    await userEvent.click(await screen.findByRole("button", { name: "Thêm danh mục" }));
    const dialog = await screen.findByRole("dialog");

    await userEvent.click(within(dialog).getByRole("button", { name: "Cây nhiều cấp" }));
    await userEvent.type(within(dialog).getByLabelText(/Mã danh mục/), "CURRICULUM");
    await userEvent.type(within(dialog).getByLabelText(/Tên tiếng Việt/), "Khung chương trình");

    await userEvent.type(within(dialog).getByLabelText("Tên cấp 1"), "Khối");
    await userEvent.type(within(dialog).getByLabelText("Tên cấp 2"), "Chủ đề");
    await userEvent.click(within(dialog).getByRole("button", { name: "Thêm cấp" }));
    await userEvent.type(within(dialog).getByLabelText("Tên cấp 3"), "Mục tiêu");

    const refSelect = within(dialog).getByRole("combobox", { name: /Danh mục liên kết/ });
    await userEvent.click(refSelect);
    await userEvent.click(await screen.findByRole("option", { name: /Môn học/ }));

    await userEvent.click(within(dialog).getByRole("button", { name: "Lưu" }));

    await waitFor(() => {
      expect(api.createGroup).toHaveBeenCalledWith({
        code: "CURRICULUM",
        nameVi: "Khung chương trình",
        status: "ACTIVE",
        kind: "TREE",
        levels: ["Khối", "Chủ đề", "Mục tiêu"],
        refGroupIds: ["grp-subject"],
      });
    });
  });

  it("tải lười nút con khi mở rộng, mở tất cả / thu gọn, đồng bộ ?node= và thao tác liên kết ở pane 340px", async () => {
    const api = createTreeMockApi();
    renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    expect(screen.getByText("Chương → Bài → Kiến thức")).toBeInTheDocument();

    // Expand CH1 lazy loads B1
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    expect(await screen.findByText("Tính đơn điệu")).toBeInTheDocument();
    expect(screen.getByText("Ngừng theo cấp trên")).toBeInTheDocument();

    // Select B1 -> updates ?node= and shows 340px detail pane with inherited + own refs
    await userEvent.click(screen.getByRole("treeitem", { name: /Tính đơn điệu/ }));
    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith(
        expect.stringContaining("node=node-b1"),
        { scroll: false },
      );
    });

    const detailPane = screen.getByRole("complementary", { name: "Chi tiết nút đang chọn" });
    expect(await within(detailPane).findByText("Toán học")).toBeInTheDocument();
    expect(within(detailPane).getByText("Ngữ văn")).toBeInTheDocument();
    // Inherited ref has no remove button; own ref has remove button
    expect(within(detailPane).queryByRole("button", { name: "Gỡ Toán học" })).not.toBeInTheDocument();
    await userEvent.click(within(detailPane).getByRole("button", { name: "Gỡ Ngữ văn" }));
    await waitFor(() => {
      expect(api.removeItemRef).toHaveBeenCalledWith("node-b1", "item-van");
    });

    // Add own ref via Select
    const addRefCombo = within(detailPane).getByRole("combobox", { name: "Gắn Môn học" });
    await userEvent.click(addRefCombo);
    await userEvent.click(await screen.findByRole("option", { name: "Tiếng Anh" }));
    await waitFor(() => {
      expect(api.addItemRef).toHaveBeenCalledWith("node-b1", "item-anh");
    });

    // Expand all -> fetches level 3 KT1; Collapse all -> hides children
    await userEvent.click(screen.getByRole("button", { name: "Mở tất cả" }));
    expect(await screen.findByText("Cực trị")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Thu gọn" }));
    await waitFor(() => {
      expect(screen.queryByText("Cực trị")).not.toBeInTheDocument();
    });
  });

  it("lọc theo danh mục liên kết, sắp xếp cùng cha, khóa xóa nút có con và ẩn thao tác ghi với CATALOG.VIEW", async () => {
    const api = createTreeMockApi();
    const { unmount } = renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    // Delete disabled on node with children
    expect(screen.getByRole("button", { name: "Xóa Hàm số" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );

    // Same-parent sibling reorder
    await userEvent.click(screen.getByRole("button", { name: "Di chuyển Tích phân lên" }));
    await waitFor(() => {
      expect(api.reorderItems).toHaveBeenCalledWith("grp-knowledge", {
        itemIds: ["node-ch2", "node-ch1"],
      });
    });

    // Filter by linked ref SUBJECT=TOAN (passes OpenAPI `ref` param)
    const filterTrigger = screen.getByRole("button", { name: "Lọc theo Môn học" });
    await userEvent.click(filterTrigger);
    await userEvent.click(await screen.findByRole("menuitemcheckbox", { name: "Toán học" }));
    await waitFor(() => {
      expect(api.listTree).toHaveBeenCalledWith("grp-knowledge", {
        ref: ["item-toan"],
      });
    });
    await userEvent.keyboard("{Escape}");
    unmount();

    // CATALOG.VIEW only: sees tree + detail action, no write controls
    renderCatalog({
      api: createTreeMockApi(),
      search: "group=KNOWLEDGE",
      permissions: ["CATALOG.VIEW"],
    });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chi tiết Hàm số" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thêm Chương/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sửa Hàm số" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa Hàm số" })).not.toBeInTheDocument();
  });

  it("hỗ trợ thêm nút con, chuyển nhánh và ngừng sử dụng kèm phạm vi nút con", async () => {
    const api = createTreeMockApi();
    renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    // Add child under CH1 -> opens TreeNodeFormDialog with parent read-only and inherited ref
    await userEvent.click(screen.getByRole("button", { name: "Thêm Bài vào Hàm số" }));
    const addDialog = await screen.findByRole("dialog");
    expect(within(addDialog).getByText("CH1")).toBeInTheDocument();
    expect(within(addDialog).getByText("Toán học")).toBeInTheDocument();
    await userEvent.type(within(addDialog).getByLabelText(/Mã giá trị/), "B2");
    await userEvent.type(within(addDialog).getByLabelText(/Tên tiếng Việt/), "Cực trị hàm số");
    await userEvent.click(within(addDialog).getByRole("button", { name: "Lưu" }));

    await waitFor(() => {
      expect(api.createItem).toHaveBeenCalledWith(
        "grp-knowledge",
        expect.objectContaining({
          parentId: "node-ch1",
          code: "B2",
          nameVi: "Cực trị hàm số",
        }),
      );
    });

    // Expand CH1 -> Move B1 to CH2
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByRole("button", { name: "Chuyển nhánh Tính đơn điệu" }));
    const moveDialog = await screen.findByRole("dialog");
    await userEvent.click(await within(moveDialog).findByRole("radio", { name: /Tích phân/ }));
    await userEvent.click(within(moveDialog).getByRole("button", { name: "Chuyển" }));

    await waitFor(() => {
      expect(api.moveItem).toHaveBeenCalledWith("node-b1", {
        parentId: "node-ch2",
        version: 1,
      });
    });

    // Stop CH1 -> StopItemDialog displays descendant impact bullet from getDescendants
    await userEvent.click(screen.getByRole("button", { name: "Ngừng sử dụng Hàm số" }));
    const stopDialog = await screen.findByRole("dialog");
    expect(
      await within(stopDialog).findByText(
        /2 nút bên dưới sẽ chuyển sang Ngừng theo cấp trên/,
      ),
    ).toBeInTheDocument();
  });

  it("cho phép chọn nhiều giá trị OR trong cùng một danh mục liên kết và gửi qua tham số ref", async () => {
    const gradeGroup: CatalogGroupResponse = {
      ...SYSTEM_KNOWLEDGE_GROUP,
      refGroups: [
        {
          refGroupId: "grp-grade",
          code: "GRADE",
          nameVi: "Khối lớp",
          nameEn: "Grade",
          status: "ACTIVE",
          isDefault: true,
          position: 0,
          linkedItemCount: 0,
        },
      ],
    };
    const gradeOptions: CatalogOptionResponse[] = [
      { id: "item-g10", code: "G10", nameVi: "Lớp 10", nameEn: "Grade 10", value: 1, displayOrder: 0 },
      { id: "item-g11", code: "G11", nameVi: "Lớp 11", nameEn: "Grade 11", value: 2, displayOrder: 1 },
    ];
    const api = createTreeMockApi({
      listGroups: vi.fn(async () => pageOfGroups([SYSTEM_SUBJECT_GROUP, gradeGroup])),
      getOptions: vi.fn(async (code) => (code === "GRADE" ? gradeOptions : [])),
    });

    renderCatalog({ api, search: "group=KNOWLEDGE" });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    const gradeFilterBtn = screen.getByRole("button", { name: "Lọc theo Khối lớp" });
    await userEvent.click(gradeFilterBtn);
    await userEvent.click(await screen.findByRole("menuitemcheckbox", { name: "Lớp 10" }));
    await userEvent.click(await screen.findByRole("menuitemcheckbox", { name: "Lớp 11" }));

    await waitFor(() => {
      expect(api.listTree).toHaveBeenCalledWith("grp-knowledge", {
        ref: ["item-g10", "item-g11"],
      });
    });
  });
});

describe("CatalogView Slice 4: xuất và nhập Excel danh mục", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    if (!URL.createObjectURL) {
      Object.defineProperty(URL, "createObjectURL", {
        writable: true,
        value: vi.fn(() => "blob:mock-url"),
      });
    } else {
      vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:mock-url");
    }
    if (!URL.revokeObjectURL) {
      Object.defineProperty(URL, "revokeObjectURL", {
        writable: true,
        value: vi.fn(),
      });
    } else {
      vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    }
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("ẩn nút Nhập Excel ở nhóm tổ hợp và với CATALOG.VIEW; giữ Xuất Excel cho cả hai", async () => {
    const api = createTreeMockApi();
    const { unmount } = renderCatalog({ api, search: "group=SUBJECT_COMBINATION" });

    expect(await screen.findByRole("button", { name: "Xuất Excel" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhập Excel" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Xuất Excel" }));
    await waitFor(() => {
      expect(api.exportItems).toHaveBeenCalledWith("grp-combination");
    });
    unmount();

    renderCatalog({
      api: createTreeMockApi(),
      search: "group=SUBJECT",
      permissions: ["CATALOG.VIEW"],
    });
    expect(await screen.findByRole("button", { name: "Xuất Excel" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhập Excel" })).not.toBeInTheDocument();
  });

  it("chặn tệp quá 5 MB và tệp .xlsm ngay ở bước chọn tệp", async () => {
    const api = createTreeMockApi();
    renderCatalog({ api, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Nhập Excel" }));
    const dialog = await screen.findByRole("dialog");
    const fileInput = within(dialog).getByLabelText(/Tệp Excel/) as HTMLInputElement;

    const oversized = new File(["x"], "big.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    Object.defineProperty(oversized, "size", { value: 5 * 1024 * 1024 + 1 });
    await userEvent.upload(fileInput, oversized);

    expect(
      await within(dialog).findByText(viMessages.catalog.errorCodes.IMPORT_FILE_TOO_LARGE),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Kiểm tra tệp" })).toBeDisabled();
    expect(api.previewImport).not.toHaveBeenCalled();

    const userNoAccept = userEvent.setup({ applyAccept: false });
    const macroFile = new File(["macro"], "macro.xlsm", {
      type: "application/vnd.ms-excel.sheet.macroEnabled.12",
    });
    await userNoAccept.upload(fileInput, macroFile);
    expect(
      await within(dialog).findByText(viMessages.catalog.errorCodes.IMPORT_MACRO_NOT_ALLOWED),
    ).toBeInTheDocument();
  });

  it("hiển thị bảng lỗi, nút tải file lỗi khi previewImport có lỗi và cho phép xác nhận khi hợp lệ", async () => {
    const api = createTreeMockApi({
      previewImport: vi
        .fn()
        .mockResolvedValueOnce({
          previewId: "prev-err",
          groupId: "grp-knowledge",
          groupCode: "KNOWLEDGE",
          groupKind: "TREE",
          totalRows: 3,
          validRows: 2,
          errorRows: 1,
          canConfirm: false,
          errors: [
            {
              rowNo: 4,
              column: "code",
              code: "CATALOG_CODE_TAKEN",
              message: "Mã đã tồn tại",
            },
          ],
        })
        .mockResolvedValueOnce({
          previewId: "prev-ok",
          groupId: "grp-knowledge",
          groupCode: "KNOWLEDGE",
          groupKind: "TREE",
          totalRows: 3,
          validRows: 3,
          errorRows: 0,
          canConfirm: true,
          errors: [],
        }),
      confirmImport: vi.fn(async () => ({
        previewId: "prev-ok",
        groupId: "grp-knowledge",
        groupCode: "KNOWLEDGE",
        totalRows: 3,
        createdCount: 2,
        reusedCount: 1,
      })),
    });

    renderCatalog({ api, search: "group=KNOWLEDGE" });
    await userEvent.click(await screen.findByRole("button", { name: "Nhập Excel" }));
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByText(viMessages.catalog.importDialog.treeRuleHint)).toBeInTheDocument();
    const fileInput = within(dialog).getByLabelText(/Tệp Excel/) as HTMLInputElement;

    const validXlsx = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], "tree.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await userEvent.upload(fileInput, validXlsx);
    await userEvent.click(within(dialog).getByRole("button", { name: "Kiểm tra tệp" }));

    expect(await within(dialog).findByText("CATALOG_CODE_TAKEN")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: /Nhập 2 dòng hợp lệ/ })).toBeDisabled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Tải file lỗi" }));
    expect(URL.createObjectURL).toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Chọn tệp khác" }));
    const fileInputRetry = within(dialog).getByLabelText(/Tệp Excel/) as HTMLInputElement;
    await userEvent.upload(fileInputRetry, validXlsx);
    await userEvent.click(within(dialog).getByRole("button", { name: "Kiểm tra tệp" }));

    const confirmBtn = await within(dialog).findByRole("button", {
      name: /Nhập 3 dòng hợp lệ/,
    });
    expect(confirmBtn).not.toBeDisabled();
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(api.confirmImport).toHaveBeenCalledWith(
        "grp-knowledge",
        "prev-ok",
        expect.any(String),
      );
    });
    expect(
      await within(dialog).findByText(/Đã nhập xong: tạo mới 2 giá trị, dùng lại 1 nút có sẵn/),
    ).toBeInTheDocument();
  });
});

describe("CatalogView: dịch cấp cây hệ thống và pickName theo useLocale()", () => {
  it("hiển thị tên 3 cấp cây hệ thống qua message key trên header, dòng cây, pane chi tiết và các dialog ở locale en & vi", async () => {
    const api = createTreeMockApi();
    const enRender = renderCatalog({ api, locale: "en", search: "group=KNOWLEDGE" });

    // Header breadcrumb & primary add button in English
    expect(await screen.findByText("Chapter → Lesson → Knowledge")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Chapter" })).toBeInTheDocument();

    // Tree row add-child button uses localized next level ("Lesson") and pickName ("Functions")
    expect(
      await screen.findByRole("button", { name: "Add Lesson under Functions" }),
    ).toBeInTheDocument();

    // Selecting chapter updates detail pane level badge + next-level button
    await userEvent.click(screen.getByText("Functions"));
    const detailPane = screen.getByRole("complementary", { name: "Selected node details" });
    expect(within(detailPane).getByText("Chapter")).toBeInTheDocument();
    expect(within(detailPane).getByRole("button", { name: "Add Lesson" })).toBeInTheDocument();

    // Expand chapter -> child row uses "Knowledge" for add-child action
    await userEvent.click(screen.getByRole("button", { name: "Expand Functions" }));
    expect(
      await screen.findByRole("button", { name: "Add Knowledge under Monotonicity" }),
    ).toBeInTheDocument();

    // Selecting lesson updates detail pane to "Lesson" + "Add Knowledge"
    await userEvent.click(screen.getByText("Monotonicity"));
    expect(within(detailPane).getByText("Lesson")).toBeInTheDocument();
    expect(within(detailPane).getByRole("button", { name: "Add Knowledge" })).toBeInTheDocument();

    // Open root create dialog -> title "Add Chapter"
    await userEvent.click(screen.getByRole("button", { name: "Add Chapter" }));
    const rootDialog = await screen.findByRole("dialog");
    expect(within(rootDialog).getByRole("heading", { name: "Add Chapter" })).toBeInTheDocument();
    await userEvent.click(within(rootDialog).getByRole("button", { name: "Cancel" }));

    // Open child create dialog under Functions -> title "Add Lesson" and parent level "Chapter"
    await userEvent.click(screen.getByRole("button", { name: "Add Lesson under Functions" }));
    const childDialog = await screen.findByRole("dialog");
    expect(within(childDialog).getByRole("heading", { name: "Add Lesson" })).toBeInTheDocument();
    expect(within(childDialog).getByText("Chapter")).toBeInTheDocument();
    await userEvent.click(within(childDialog).getByRole("button", { name: "Cancel" }));

    // Open move branch dialog for B1 -> summary includes "Moving Lesson"
    await userEvent.click(screen.getByRole("button", { name: "Move branch Monotonicity" }));
    const moveDialog = await screen.findByRole("dialog");
    expect(within(moveDialog).getByText(/Moving: Lesson B1 — Monotonicity/)).toBeInTheDocument();
    await userEvent.click(within(moveDialog).getByRole("button", { name: "Cancel" }));

    enRender.unmount();

    // Verify Vietnamese locale renders Vietnamese level keys via useLocale()
    renderCatalog({ api: createTreeMockApi(), locale: "vi", search: "group=KNOWLEDGE" });
    expect(await screen.findByText("Chương → Bài → Kiến thức")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thêm Chương" })).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Thêm Bài vào Hàm số" }),
    ).toBeInTheDocument();
  });

  it("giữ nguyên tên cấp do người dùng nhập cho cây tùy chỉnh ở locale en và kiểm tra fallback pickName theo useLocale()", async () => {
    const customTreeGroup: CatalogGroupResponse = {
      id: "grp-custom-tree",
      code: "CUSTOM_TREE",
      nameVi: "Khung tùy chỉnh",
      nameEn: "   ",
      description: "Custom tree with custom levels",
      kind: "TREE",
      type: "CUSTOM",
      system: false,
      status: "ACTIVE",
      itemCount: 1,
      levels: ["Khối", "Chủ đề", "Chương"],
      refGroups: [],
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
      version: 0,
    };
    const customRootNode: CatalogTreeNodeResponse = {
      id: "node-k10",
      groupId: "grp-custom-tree",
      code: "K10",
      nameVi: "Khối 10 mặc định",
      nameEn: undefined,
      displayOrder: 0,
      status: "ACTIVE",
      effectiveStatus: "ACTIVE",
      depth: 1,
      path: "/node-k10/",
      hasChildren: false,
      childCount: 0,
      matched: false,
      ownRefs: [],
      inheritedRefs: [],
      value: 1,
      version: 0,
    };

    const api = createTreeMockApi({
      listGroups: vi.fn(async () =>
        pageOfGroups([SYSTEM_SUBJECT_GROUP, SYSTEM_KNOWLEDGE_GROUP, customTreeGroup]),
      ),
      listTree: vi.fn(async (groupId) =>
        groupId === "grp-custom-tree" ? [customRootNode] : [KNOWLEDGE_CH1, KNOWLEDGE_CH2],
      ),
      getItem: vi.fn(async (id) =>
        id === "node-k10" ? toTreeItemDetail(customRootNode) : toTreeItemDetail(KNOWLEDGE_CH1),
      ),
    });

    renderCatalog({ api, locale: "en", search: "group=CUSTOM_TREE" });

    // Custom levels stay verbatim (including "Chương" when entered on a custom group)
    expect(await screen.findByText("Khối → Chủ đề → Chương")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Khối" })).toBeInTheDocument();

    // pickName falls back nameEn -> nameVi when nameEn is whitespace or null under locale="en"
    expect(screen.getByRole("heading", { name: "Khung tùy chỉnh" })).toBeInTheDocument();
    expect(await screen.findByText("Khối 10 mặc định")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Chủ đề under Khối 10 mặc định" })).toBeInTheDocument();

    await userEvent.click(screen.getByText("Khối 10 mặc định"));
    const detailPane = screen.getByRole("complementary", { name: "Selected node details" });
    expect(within(detailPane).getByText("Khối")).toBeInTheDocument();
    expect(within(detailPane).getByRole("button", { name: "Add Chủ đề" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Add Chủ đề under Khối 10 mặc định" }));
    const childDialog = await screen.findByRole("dialog");
    expect(within(childDialog).getByRole("heading", { name: "Add Chủ đề" })).toBeInTheDocument();
    expect(within(childDialog).getByText("Khối")).toBeInTheDocument();
  });
});
