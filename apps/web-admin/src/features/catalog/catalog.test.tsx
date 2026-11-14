import { MutationCache, QueryClient } from "@tanstack/react-query";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { createTranslator } from "next-intl";
import { useMemo, useSyncExternalStore } from "react";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { ApiStatusError } from "@/lib/api/query-client";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  CatalogApiError,
  type CatalogApi,
  type CatalogGroupRefResponse,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type CatalogTreeNodeResponse,
  type CatalogOptionResponse,
  type CombinationVersionResponse,
  type PageResponseCatalogGroupResponse,
  type PageResponseCatalogItemResponse,
} from "./api";
import { CatalogView } from "./components/catalog-view";
import { buildCatalogDndAccessibility, type CatalogDndMessageKey } from "./dnd-accessibility";
import { resolveTreeDragReorder } from "./components/tree-items-panel";
import {
  CATALOG_ERROR_CODES,
  pickName,
  resolveCatalogErrorMessage,
  resolveImportColumnLabel,
  resolveSystemTreeLevelKey,
  resolveTreeLevelLabel,
  type SystemTreeLevelKey,
} from "./utils";

const routerMock = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const state = {
    pathname: "/cau-hinh-chung",
    search: "",
    /** Simulates Next >= 14.1 mirroring `history.replaceState` into `useSearchParams`. */
    syncFromHistory: (url: string) => {
      const idx = url.indexOf("?");
      state.search = idx >= 0 ? url.slice(idx + 1) : "";
      for (const listener of listeners) listener();
    },
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
    getGroupUsages: vi.fn(async (groupId) => ({
      targetId: groupId,
      targetCode: "SOURCE",
      totalCount: 0,
      blocking: false,
      usages: [],
    })),
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
  client,
}: {
  permissions?: readonly string[];
  api?: CatalogApi;
  locale?: string;
  search?: string;
  client?: QueryClient;
} = {}) {
  routerMock.search = search;
  const activeMessages = normalizeUiLocale(locale) === "en" ? en : viMessages;
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
      { locale, messages: activeMessages, client },
    ),
  };
}

let replaceStateSpy: MockInstance<History["replaceState"]>;

beforeEach(() => {
  routerMock.search = "";
  replaceStateSpy = vi
    .spyOn(window.history, "replaceState")
    .mockImplementation((_data, _unused, url) => {
      routerMock.syncFromHistory(String(url));
    });
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
    expect(pickName({ nameVi: "Toán học", nameEn: "Mathematics" }, "en-GB")).toBe("Mathematics");
    expect(pickName({ nameVi: "   ", nameEn: "Physics" }, "vi")).toBe("Physics");
  });
});

describe("resolveTreeLevelLabel", () => {
  it("dịch 3 cấp hệ thống (Chương, Bài, Kiến thức) theo vi/en và giữ nguyên cấp tùy chỉnh", () => {
    expect(resolveSystemTreeLevelKey("KNOWLEDGE", 0)).toBe("chapter");
    expect(resolveSystemTreeLevelKey("KNOWLEDGE", 1)).toBe("lesson");
    expect(resolveSystemTreeLevelKey("KNOWLEDGE", 2)).toBe("knowledge");
    expect(resolveSystemTreeLevelKey("KNOWLEDGE", 3)).toBeNull();
    expect(resolveSystemTreeLevelKey("CUSTOM_TREE", 0)).toBeNull();
    expect(resolveSystemTreeLevelKey("toString", 0)).toBeNull();
    expect(resolveSystemTreeLevelKey(undefined, 0)).toBeNull();

    const systemLevels = ["Chương", "Bài", "Kiến thức"];
    expect(
      systemLevels.map((lvl, levelIndex) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: true,
          groupCode: "KNOWLEDGE",
          levelIndex,
          translateLevel: (key) => viMessages.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Chương", "Bài", "Kiến thức"]);

    expect(
      systemLevels.map((lvl, levelIndex) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: true,
          groupCode: "KNOWLEDGE",
          levelIndex,
          translateLevel: (key) => en.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Chapter", "Lesson", "Knowledge"]);

    const customLevels = ["Khối", "Chủ đề", "Chương"];
    expect(
      customLevels.map((lvl, levelIndex) =>
        resolveTreeLevelLabel(lvl, {
          isSystemGroup: false,
          groupCode: "KNOWLEDGE",
          levelIndex,
          translateLevel: (key) => en.catalog.tree.systemLevels[key],
        }),
      ),
    ).toEqual(["Khối", "Chủ đề", "Chương"]);

    expect(
      resolveTreeLevelLabel(undefined, {
        isSystemGroup: true,
        groupCode: "KNOWLEDGE",
        levelIndex: 0,
        translateLevel: (key) => en.catalog.tree.systemLevels[key],
        fallback: "Root",
      }),
    ).toBe("Root");
  });
});

describe("resolveImportColumnLabel", () => {
  const translate = (key: string, values?: { level: string }) =>
    ({
      code: "Mã",
      nameVi: "Tên tiếng Việt",
      nameEn: "Tên tiếng Anh",
      description: "Mô tả",
      status: "Trạng thái",
      levelCode: `Mã ${values?.level}`,
      levelNameVi: `${values?.level} (Tiếng Việt)`,
      levelNameEn: `${values?.level} (Tiếng Anh)`,
      refItems: "Danh mục liên kết",
    })[key] as string;
  const base = {
    levels: ["Chương", "Bài", "Kiến thức"],
    isSystemGroup: false,
    groupCode: "CUSTOM_TREE",
    refGroups: [{ code: "SUBJECT", nameVi: "Môn học", nameEn: "Subject" }],
    locale: "vi",
    translate,
    translateLevel: (key: SystemTreeLevelKey) => viMessages.catalog.tree.systemLevels[key],
  };

  it("ánh xạ trường nhóm danh sách sang tiêu đề cột Excel", () => {
    expect(resolveImportColumnLabel("code", base)).toBe("Mã");
    expect(resolveImportColumnLabel("nameVi", base)).toBe("Tên tiếng Việt");
    expect(resolveImportColumnLabel("status", base)).toBe("Trạng thái");
  });

  it("ánh xạ cột cấp của cây theo tên cấp đã lưu", () => {
    expect(resolveImportColumnLabel("level1Code", base)).toBe("Mã Chương");
    expect(resolveImportColumnLabel("level2NameVi", base)).toBe("Bài (Tiếng Việt)");
    expect(resolveImportColumnLabel("level3NameEn", base)).toBe("Kiến thức (Tiếng Anh)");
    expect(resolveImportColumnLabel("level4Code", base)).toBe("level4Code");
  });

  it.each([
    ["vi", viMessages.catalog.tree.systemLevels, ["Mã Chương", "Bài (Tiếng Việt)", "Kiến thức (Tiếng Anh)"]],
    ["en", en.catalog.tree.systemLevels, ["Mã Chapter", "Lesson (Tiếng Việt)", "Knowledge (Tiếng Anh)"]],
  ])(
    "R3-01: cột cấp của cây hệ thống KNOWLEDGE dùng khóa dịch ở locale %s",
    (locale, systemLevels, expected) => {
      const system = {
        ...base,
        locale,
        isSystemGroup: true,
        groupCode: "KNOWLEDGE",
        translateLevel: (key: SystemTreeLevelKey) => systemLevels[key],
      };
      expect([
        resolveImportColumnLabel("level1Code", system),
        resolveImportColumnLabel("level2NameVi", system),
        resolveImportColumnLabel("level3NameEn", system),
      ]).toEqual(expected);
    },
  );

  it("cột cấp của cây tùy chỉnh giữ nguyên tên đã lưu dù trùng tên cấp hệ thống", () => {
    expect(
      resolveImportColumnLabel("level1Code", {
        ...base,
        locale: "en",
        translateLevel: (key) => en.catalog.tree.systemLevels[key],
      }),
    ).toBe("Mã Chương");
  });

  it("cột danh mục liên kết lấy tên nhóm theo ngôn ngữ, dự phòng nguyên văn", () => {
    expect(resolveImportColumnLabel("SUBJECT", base)).toBe("Môn học");
    expect(resolveImportColumnLabel("SUBJECT", { ...base, locale: "en" })).toBe("Subject");
    expect(resolveImportColumnLabel("refItemIds", base)).toBe("Danh mục liên kết");
    expect(resolveImportColumnLabel("OTHER", base)).toBe("OTHER");
  });
});

describe("CatalogView: cột thao tác của bảng giá trị (VIS-8)", { timeout: 20_000 }, () => {
  it("cột thao tác dính mép phải nên vẫn bấm được khi bảng rộng hơn thẻ", async () => {
    renderCatalog({ search: "group=SUBJECT" });
    const table = await screen.findByRole("table");
    const header = within(table).getByRole("columnheader", { name: "Thao tác" });
    expect(header).toHaveClass("sticky", "right-0", "bg-table-head");

    const row = (await within(table).findByText("Toán học")).closest("tr")!;
    const actionsCell = within(row).getByRole("button", { name: "Sửa Toán học" }).closest("td")!;
    expect(actionsCell).toHaveClass("sticky", "right-0", "bg-surface");
    expect(within(actionsCell).getByRole("button", { name: "Xóa Toán học" })).toBeInTheDocument();

    // Tên tiếng Anh và Giá trị chỉ hiện từ 1360px để bảng vừa thẻ ở ~1280px (cả khung xương và dòng dữ liệu).
    for (const name of ["Tên tiếng Anh", "Giá trị"]) {
      expect(within(table).getByRole("columnheader", { name })).toHaveClass(
        "hidden",
        "min-[1360px]:table-cell",
      );
    }
    expect(within(row).getByText("Mathematics")).toHaveClass("hidden", "min-[1360px]:table-cell");
  });
});

describe("thông báo thiếu quyền xem lịch sử không lộ mã quyền (VIS-12)", () => {
  it("vi và en không chứa mã quyền dạng MODULE.ACTION", () => {
    const permissionCode = /\b[A-Z]{2,}\.[A-Z_]{2,}\b/;
    expect(viMessages.catalog.detailDrawer.auditPermissionDenied).not.toMatch(permissionCode);
    expect(en.catalog.detailDrawer.auditPermissionDenied).not.toMatch(permissionCode);
  });
});

describe("CatalogView: một bộ định dạng ngày trong ngăn chi tiết (VIS-9)", { timeout: 20_000 }, () => {
  // 2026-11-14T00:00:00Z = 07:00 giờ Việt Nam (Asia/Ho_Chi_Minh, UTC+7).
  const INSTANT = "2026-11-14T00:00:00Z";

  async function readDrawerDates(locale: string) {
    const api = createMockApi({
      listItemVersions: vi.fn(async () =>
        COMBINATION_VERSIONS.map((version) => ({ ...version, createdAt: INSTANT })),
      ),
    });
    renderCatalog({ api, locale, search: "group=SUBJECT_COMBINATION" });
    await userEvent.click(
      await screen.findByRole("button", { name: locale === "en" ? "Details for QD01" : "Chi tiết QĐ01" }),
    );
    const drawer = await screen.findByRole("dialog");
    const createdLabel = locale === "en" ? "Created at" : "Ngày tạo";
    const created = within(drawer).getByText(createdLabel).nextElementSibling?.textContent;

    await userEvent.click(
      within(drawer).getByRole("tab", { name: locale === "en" ? "Versions" : "Phiên bản" }),
    );
    const versionDate = (await within(drawer).findAllByText(/\d{2}:\d{2}/))[0]?.textContent;
    return { created, versionDate };
  }

  it("ngày tạo và ngày của phiên bản cho cùng thời điểm thì hiển thị y hệt (vi)", async () => {
    const { created, versionDate } = await readDrawerDates("vi");
    expect(created).toBe("07:00 14/11/2026");
    expect(versionDate).toBe(created);
  });

  it("ngày tạo và ngày của phiên bản y hệt và theo dd/MM/yyyy ở en", async () => {
    const { created, versionDate } = await readDrawerDates("en");
    expect(created).toMatch(/^14\/11\/2026,? 07:00$/);
    expect(versionDate).toBe(created);
  });
});

describe("CatalogView: chip danh mục liên kết mặc định (VIS-6)", { timeout: 20_000 }, () => {
  it("chữ (Mặc định) dùng token chữ primary, không giảm opacity", async () => {
    renderCatalog({ api: createTreeMockApi(), search: "group=KNOWLEDGE" });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    const suffix = (await screen.findAllByText(/^\(Mặc định\)$/))[0]!;
    // Tương phản >= 4.5:1 của primary-text trên primary-50/100 ở cả hai chế độ do theme.test.ts bảo đảm.
    expect(suffix).toHaveClass("text-primary-text");
    expect(suffix.className).not.toMatch(/opacity/);
  });
});

describe("buildCatalogDndAccessibility (VIS-5)", () => {
  const names = new Map([
    ["01a1d874-0000-7000-8000-000000000001", "Toán học"],
    ["01a1d874-0000-7000-8000-000000000002", "Vật lí"],
  ]);
  const translate = (key: CatalogDndMessageKey, values?: { name: string; target?: string }) =>
    viMessages.catalog.dnd[key]
      .replace("{name}", values?.name ?? "")
      .replace("{target}", values?.target ?? "");
  const a11y = buildCatalogDndAccessibility(translate, (id) => names.get(id) ?? id);
  const [first, second] = [...names.keys()] as [string, string];
  const active = { id: first } as never;
  const over = { id: second } as never;

  it("đọc tên mục chứ không đọc UUID, bằng ngôn ngữ giao diện", () => {
    expect(a11y.announcements.onDragStart?.({ active })).toBe("Đã nhấc mục Toán học.");
    expect(a11y.announcements.onDragOver?.({ active, over })).toBe(
      "Mục Toán học đang nằm trên Vật lí.",
    );
    expect(a11y.announcements.onDragEnd?.({ active, over })).toBe(
      "Đã thả mục Toán học lên Vật lí.",
    );
    expect(a11y.screenReaderInstructions.draggable).toBe(viMessages.catalog.dnd.instructions);
  });

  it("không đọc \"đang nằm trên chính nó\" ngay sau khi nhấc; chỉ đọc khi đã qua mục khác và khi quay về chỗ cũ (V2-6)", () => {
    const fresh = buildCatalogDndAccessibility(translate, (id) => names.get(id) ?? id);
    const { onDragStart, onDragOver, onDragEnd } = fresh.announcements;
    expect(onDragStart?.({ active })).toBe("Đã nhấc mục Toán học.");
    // dnd-kit bắn onDragOver ngay sau onDragStart với over = chính mục đang kéo.
    expect(onDragOver?.({ active, over: active as never })).toBeUndefined();
    expect(onDragOver?.({ active, over })).toBe("Mục Toán học đang nằm trên Vật lí.");
    expect(onDragOver?.({ active, over: active as never })).toBe(
      "Mục Toán học đã về vị trí ban đầu.",
    );
    expect(onDragEnd?.({ active, over: active as never })).toBe("Đã thả mục Toán học.");
    // Lượt kéo kế tiếp lại bắt đầu im lặng.
    expect(onDragStart?.({ active })).toBe("Đã nhấc mục Toán học.");
    expect(onDragOver?.({ active, over: active as never })).toBeUndefined();
  });

  it("dự phòng id khi không tìm thấy tên", () => {
    expect(a11y.announcements.onDragCancel?.({ active: { id: "x" } as never, over: null })).toBe(
      "Đã hủy kéo mục x.",
    );
  });
});

describe("CatalogView: hướng dẫn kéo thả được dịch trên mọi DndContext (VIS-5)", { timeout: 20_000 }, () => {
  const ENGLISH_DEFAULT = /To pick up a draggable item/;

  it("bảng giá trị SIMPLE: vi và en", async () => {
    const viRender = renderCatalog({ search: "group=SUBJECT" });
    expect(await screen.findByText("Toán học")).toBeInTheDocument();
    expect(screen.getByText(viMessages.catalog.dnd.instructions)).toBeInTheDocument();
    expect(screen.queryByText(ENGLISH_DEFAULT)).not.toBeInTheDocument();
    viRender.unmount();

    renderCatalog({ locale: "en", search: "group=SUBJECT" });
    expect(await screen.findByText("Mathematics")).toBeInTheDocument();
    expect(screen.getByText(en.catalog.dnd.instructions)).toBeInTheDocument();
    expect(screen.queryByText(ENGLISH_DEFAULT)).not.toBeInTheDocument();
  });

  it("tay nắm sắp xếp có aria-roledescription theo ngôn ngữ, không còn \"sortable\" của dnd-kit (V2-6)", async () => {
    const viRender = renderCatalog({ search: "group=SUBJECT" });
    expect(await screen.findByText("Toán học")).toBeInTheDocument();
    const viHandles = document.querySelectorAll("[aria-roledescription]");
    expect(viHandles.length).toBeGreaterThan(0);
    for (const el of viHandles) {
      expect(el).toHaveAttribute("aria-roledescription", viMessages.catalog.dnd.roleDescription);
    }
    viRender.unmount();

    renderCatalog({ locale: "en", search: "group=SUBJECT" });
    expect(await screen.findByText("Mathematics")).toBeInTheDocument();
    const enHandles = document.querySelectorAll("[aria-roledescription]");
    expect(enHandles.length).toBeGreaterThan(0);
    for (const el of enHandles) {
      expect(el).toHaveAttribute("aria-roledescription", en.catalog.dnd.roleDescription);
    }
  });

  it("danh sách môn trong hộp thoại tổ hợp", async () => {
    renderCatalog({ search: "group=SUBJECT_COMBINATION" });
    await userEvent.click(await screen.findByRole("button", { name: "Sửa QĐ01" }));
    await screen.findByRole("dialog");
    await screen.findByRole("button", { name: "Di chuyển Ngữ văn (VAN) lên" });

    // Bảng + danh sách môn: mỗi DndContext một bản hướng dẫn, không bản nào là mặc định tiếng Anh.
    expect(screen.getAllByText(viMessages.catalog.dnd.instructions).length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText(ENGLISH_DEFAULT)).not.toBeInTheDocument();
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

describe("CatalogView: phân quyền và bốn trạng thái", { timeout: 20_000 }, () => {
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
    expect(replaceStateSpy).toHaveBeenCalledWith(
      null,
      "",
      "/cau-hinh-chung?group=QUESTION_SOURCE",
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

describe("CatalogView: bảng giá trị, tìm kiếm, lọc và sắp xếp thứ tự", { timeout: 20_000 }, () => {
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

describe("CatalogView: dialog nhóm và dialog giá trị", { timeout: 20_000 }, () => {
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

describe("CatalogView Slice 2: tổ hợp, ngăn chi tiết, ngừng sử dụng và xóa đang dùng", { timeout: 20_000 }, () => {
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
    // VIS-12: không lộ mã quyền (AUDIT.VIEW) trong chữ người dùng thấy
    expect(within(simpleDrawer).queryByText(/[A-Z]{2,}\.[A-Z_]{2,}/)).not.toBeInTheDocument();
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

  it("cột Value và nhãn giá trị hệ thống dùng tiếng Việt ở vi, giữ tiếng Anh ở en (VIS-3)", async () => {
    const viRender = renderCatalog({ search: "group=SUBJECT" });
    const viTable = await screen.findByRole("table");
    expect(within(viTable).getByRole("columnheader", { name: "Giá trị" })).toBeInTheDocument();
    expect(within(viTable).queryByRole("columnheader", { name: "Value" })).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Chi tiết Toán học" }));
    const viDrawer = await screen.findByRole("dialog");
    expect(within(viDrawer).getByText("Giá trị hệ thống")).toBeInTheDocument();
    viRender.unmount();

    renderCatalog({ locale: "en", search: "group=SUBJECT" });
    const enTable = await screen.findByRole("table");
    expect(within(enTable).getByRole("columnheader", { name: "Value" })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Details for Mathematics" }));
    expect(within(await screen.findByRole("dialog")).getByText("System value")).toBeInTheDocument();
  });

  it("đóng ngăn chi tiết ngay khi xóa giá trị đang mở và đặt lại itemDialog khi đổi danh mục (R1-12)", async () => {
    const api = createMockApi();
    renderCatalog({ api, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Chi tiết Toán học" }));
    const drawer = await screen.findByRole("dialog");
    expect(within(drawer).getAllByText("TOAN").length).toBeGreaterThan(0);

    // Trigger delete while detail drawer is open
    fireEvent.click(screen.getByRole("button", { name: "Xóa Toán học", hidden: true }));
    const confirmDlg = await screen.findByRole("dialog", { name: "Xóa giá trị?" });
    await userEvent.type(within(confirmDlg).getByLabelText(/Lý do xóa/), "Dọn dữ liệu test");
    await userEvent.click(within(confirmDlg).getByRole("button", { name: "Xóa giá trị" }));

    await waitFor(() => expect(api.deleteItem).toHaveBeenCalledWith("item-toan", "Dọn dữ liệu test"));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("truy vấn getGroupUsages và khóa Lưu khi nhóm tùy chỉnh chuyển Ngừng sử dụng bị chặn hoặc lỗi tải (R1-15)", async () => {
    const getGroupUsages = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce({
        targetId: "grp-source",
        targetCode: "SOURCE",
        totalCount: 3,
        blocking: true,
        usages: [
          {
            source: "Ngân hàng câu hỏi",
            objectType: "QUESTION",
            count: 3,
            blocking: true,
            sampleLabels: ["Q-001"],
          },
        ],
      });
    const api = createMockApi({ getGroupUsages });
    renderCatalog({ api, search: "group=QUESTION_SOURCE" });

    expect(await screen.findByRole("heading", { name: "Nguồn câu hỏi" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thao tác danh mục" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Sửa danh mục" }));

    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Trạng thái/ }));
    await userEvent.click(await screen.findByRole("option", { name: "Ngừng sử dụng" }));

    await waitFor(() => expect(getGroupUsages).toHaveBeenCalledWith("grp-source"));
    expect(
      await within(dialog).findByText(viMessages.catalog.stopItemDialog.usagesLoadError),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Lưu" })).toBeDisabled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Thử lại" }));
    expect(
      await within(dialog).findByText(viMessages.catalog.stopItemDialog.blockedAlert),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("Ngân hàng câu hỏi (3)")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Lưu" })).toBeDisabled();
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
    listRefGroups: vi.fn(async (groupId) => {
      if (overrides.listGroups) {
        const page = await overrides.listGroups();
        const found = page.items.find((g) => g.id === groupId);
        if (found) return found.refGroups;
      }
      return groupId === SYSTEM_KNOWLEDGE_GROUP.id ? SYSTEM_KNOWLEDGE_GROUP.refGroups : [];
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

describe("CatalogView Slice 3: danh mục cây nhiều cấp, liên kết và chuyển nhánh", { timeout: 20_000 }, () => {
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

  // V2-1: jsdom không có layout; hợp đồng lớp của trang cây. Ô chi tiết 340px chỉ đặt cạnh cây từ 1600px.
  it("cây: ô chi tiết xếp dưới cây dưới 1600px, hàng cây giữ tên và thu liên kết thành +N (V2-1)", async () => {
    renderCatalog({ api: createTreeMockApi(), search: "group=KNOWLEDGE" });

    const name = await screen.findByText("Hàm số");
    expect(name).toHaveClass("min-w-[8rem]", "flex-1", "truncate");

    const pane = screen.getByRole("complementary", { name: viMessages.catalog.tree.detailTitle });
    expect(pane).toHaveClass("w-full", "min-[1600px]:w-[340px]");
    expect(pane.className).not.toMatch(/(^|\s)(xl|lg|md):/);
    expect(pane.parentElement).toHaveClass("flex-col", "min-[1600px]:flex-row");
    expect(pane.parentElement?.className).not.toMatch(/(^|\s)(xl|lg|md):/);

    const row = screen.getByRole("treeitem", { name: "Hàm số" });
    expect(within(row).getByText("Toán học")).toHaveClass("@3xl:inline");
    expect(within(row).getByRole("img", { name: "Toán học" })).toHaveTextContent("+1");
    expect(within(row).getByText("CH1")).toHaveClass("max-w-24", "truncate");
  });

  // V3-1: jsdom không có layout; matchMedia giả lập bề rộng (>= 1600px: ô cạnh cây) và giảm chuyển động.
  describe("cây: đưa ô chi tiết xếp dưới cây vào vùng nhìn thấy khi chọn nút (V3-1)", () => {
    let scrollSpy: MockInstance<Element["scrollIntoView"]>;

    function mockMedia({ wide, reducedMotion }: { wide: boolean; reducedMotion: boolean }) {
      vi.spyOn(window, "matchMedia").mockImplementation(
        (query: string) =>
          ({
            matches: query.includes("min-width: 1600px")
              ? wide
              : query.includes("prefers-reduced-motion")
                ? reducedMotion
                : false,
            media: query,
            onchange: null,
            addEventListener: () => {},
            removeEventListener: () => {},
            addListener: () => {},
            removeListener: () => {},
            dispatchEvent: () => false,
          }) as MediaQueryList,
      );
    }

    beforeEach(() => {
      scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    function paneScrolls() {
      const pane = screen.getByRole("complementary", { name: viMessages.catalog.tree.detailTitle });
      return scrollSpy.mock.contexts.filter((ctx) => ctx === pane).length;
    }

    it.each([
      { reducedMotion: false, behavior: "smooth" },
      { reducedMotion: true, behavior: "auto" },
    ])(
      "dưới 1600px: chọn nút cuộn ô vào view (behavior=$behavior) và chuyển tiêu điểm về tiêu đề ô",
      async ({ reducedMotion, behavior }) => {
        mockMedia({ wide: false, reducedMotion });
        renderCatalog({ api: createTreeMockApi(), search: "group=KNOWLEDGE" });

        await userEvent.click(await screen.findByRole("treeitem", { name: "Hàm số" }));

        const pane = screen.getByRole("complementary", { name: viMessages.catalog.tree.detailTitle });
        const heading = await within(pane).findByRole("heading", { level: 3, name: "Hàm số" });
        await waitFor(() => expect(heading).toHaveFocus());
        expect(heading).toHaveAttribute("tabindex", "-1");
        expect(paneScrolls()).toBe(1);
        const call = scrollSpy.mock.calls[scrollSpy.mock.contexts.indexOf(pane)];
        expect(call?.[0]).toEqual({ block: "nearest", behavior });
      },
    );

    it("từ 1600px (ô cạnh cây): không cuộn, không chuyển tiêu điểm", async () => {
      mockMedia({ wide: true, reducedMotion: false });
      renderCatalog({ api: createTreeMockApi(), search: "group=KNOWLEDGE" });

      await userEvent.click(await screen.findByRole("treeitem", { name: "Hàm số" }));

      const pane = screen.getByRole("complementary", { name: viMessages.catalog.tree.detailTitle });
      const heading = await within(pane).findByRole("heading", { level: 3, name: "Hàm số" });
      expect(paneScrolls()).toBe(0);
      expect(heading).not.toHaveFocus();
    });

    it("mở liên kết sâu ?node= dưới 1600px: không cuộn, không cướp tiêu điểm", async () => {
      mockMedia({ wide: false, reducedMotion: false });
      renderCatalog({ api: createTreeMockApi(), search: "group=KNOWLEDGE&node=node-ch1" });

      const pane = await screen.findByRole("complementary", {
        name: viMessages.catalog.tree.detailTitle,
      });
      const heading = await within(pane).findByRole("heading", { level: 3, name: "Hàm số" });
      expect(paneScrolls()).toBe(0);
      expect(heading).not.toHaveFocus();
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
      expect(replaceStateSpy).toHaveBeenCalledWith(
        null,
        "",
        expect.stringContaining("node=node-b1"),
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

    // Delete disabled on node with children (via row actions dropdown)
    await userEvent.click(screen.getByRole("button", { name: "Thao tác của Hàm số" }));
    expect(await screen.findByRole("menuitem", { name: "Không xóa được vì còn nút con bên dưới." })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await userEvent.keyboard("{Escape}");

    // Same-parent sibling reorder via row actions dropdown
    await userEvent.click(screen.getByRole("button", { name: "Thao tác của Tích phân" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Di chuyển Tích phân lên" }));
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
    expect(screen.queryByRole("button", { name: "Thao tác của Hàm số" })).not.toBeInTheDocument();
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

    // Expand CH1 -> Move B1 to CH2 via row dropdown
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByRole("button", { name: "Thao tác của Tính đơn điệu" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Chuyển nhánh" }));
    const moveDialog = await screen.findByRole("dialog");
    await userEvent.click(await within(moveDialog).findByRole("radio", { name: /Tích phân/ }));
    await userEvent.click(within(moveDialog).getByRole("button", { name: "Chuyển" }));

    await waitFor(() => {
      expect(api.moveItem).toHaveBeenCalledWith("node-b1", {
        parentId: "node-ch2",
        version: 1,
      });
    });

    // Stop CH1 via row dropdown -> StopItemDialog displays descendant impact bullet from getDescendants
    await userEvent.click(screen.getByRole("button", { name: "Thao tác của Hàm số" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Ngừng sử dụng" }));
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

  it("hiển thị tay cầm kéo thả cho các nút cùng cha khi có quyền và không lọc, ẩn khi lọc hoặc chỉ có CATALOG.VIEW", async () => {
    const api = createTreeMockApi();
    const { unmount } = renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    // CH1 & CH2 are siblings -> both have drag handles
    expect(screen.getByRole("button", { name: "Kéo để đổi thứ tự Hàm số" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kéo để đổi thứ tự Tích phân" })).toBeInTheDocument();

    // Expand CH1: B1 is an only child (siblingCount=1) -> no drag handle
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    expect(await screen.findByText("Tính đơn điệu")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Kéo để đổi thứ tự Tính đơn điệu" }),
    ).not.toBeInTheDocument();

    // Verify resolveTreeDragReorder allows same-parent siblings and rejects cross-parent drops
    const siblingRows = new Map([
      [
        KNOWLEDGE_CH1.id,
        {
          node: KNOWLEDGE_CH1,
          level: 1,
          hasChildren: true,
          isExpanded: true,
          siblingIndex: 0,
          siblingCount: 2,
          siblings: [KNOWLEDGE_CH1, KNOWLEDGE_CH2],
        },
      ],
      [
        KNOWLEDGE_CH2.id,
        {
          node: KNOWLEDGE_CH2,
          level: 1,
          hasChildren: false,
          isExpanded: false,
          siblingIndex: 1,
          siblingCount: 2,
          siblings: [KNOWLEDGE_CH1, KNOWLEDGE_CH2],
        },
      ],
      [
        KNOWLEDGE_BAI1.id,
        {
          node: KNOWLEDGE_BAI1,
          level: 2,
          hasChildren: true,
          isExpanded: false,
          siblingIndex: 0,
          siblingCount: 1,
          siblings: [KNOWLEDGE_BAI1],
        },
      ],
    ]);
    expect(resolveTreeDragReorder(siblingRows, KNOWLEDGE_CH1.id, KNOWLEDGE_CH2.id)).toEqual({
      parentId: null,
      siblings: [KNOWLEDGE_CH1, KNOWLEDGE_CH2],
      fromIndex: 0,
      toIndex: 1,
    });
    expect(resolveTreeDragReorder(siblingRows, KNOWLEDGE_CH1.id, KNOWLEDGE_BAI1.id)).toBeNull();

    // Filtering hides drag handles
    await userEvent.type(screen.getByLabelText("Tìm nút theo mã hoặc tên"), "Hàm");
    await waitFor(() => {
      expect(
        screen.queryByRole("button", { name: "Kéo để đổi thứ tự Hàm số" }),
      ).not.toBeInTheDocument();
    });
    unmount();

    // CATALOG.VIEW only hides drag handles
    renderCatalog({
      api: createTreeMockApi(),
      search: "group=KNOWLEDGE",
      permissions: ["CATALOG.VIEW"],
    });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Kéo để đổi thứ tự/ }),
    ).not.toBeInTheDocument();
  });


  it("thông báo kéo thả bằng tiếng Việt qua accessibility của DndContext (R2-01)", async () => {
    const api = createTreeMockApi();
    const viRender = renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    // Screen-reader instructions on draggable rows are localized, not dnd-kit defaults
    expect(
      screen.getByText(viMessages.catalog.dnd.instructions),
    ).toBeInTheDocument();
    expect(screen.queryByText(/To pick up a draggable item/)).not.toBeInTheDocument();

    // Keyboard drag: lift CH1, move over CH2, drop -> localized live announcements.
    // jsdom has no layout, so give each sortable row a distinct rect; KeyboardEvent.code
    // is also unset for userEvent, so fire explicit codes.
    const tree = screen.getByRole("tree");
    const rowTops = new Map<Element, number>();
    let rowIndex = 0;
    for (const wrapper of tree.querySelectorAll('div[role="none"]')) {
      rowTops.set(wrapper, rowIndex * 48);
      rowIndex += 1;
    }
    const originalRect = Element.prototype.getBoundingClientRect;
    const rectSpy = vi
      .spyOn(Element.prototype, "getBoundingClientRect")
      .mockImplementation(function (this: Element) {
        const top = rowTops.get(this);
        if (top === undefined) {
          return originalRect.call(this);
        }
        return new DOMRect(0, top, 400, 40);
      });

    const handle = screen.getByRole("button", { name: "Kéo để đổi thứ tự Hàm số" });
    handle.focus();
    fireEvent.keyDown(handle, { code: "Space", key: " " });
    // V2-6: the pickup announcement stays (dnd-kit's first onDragOver has over = the item itself and
    // is no longer read as "over itself"). Wait for the sensor's async listener attach, then move
    // over CH2 (two presses: the first only repositions the collision rect).
    await waitFor(() =>
      expect(
        screen.getByText(viMessages.catalog.dnd.pickedUp.replace("{name}", "Hàm số")),
      ).toBeInTheDocument(),
    );
    expect(
      screen.queryByText(
        viMessages.catalog.dnd.draggedOverTarget
          .replace("{name}", "Hàm số")
          .replace("{target}", "Hàm số"),
      ),
    ).not.toBeInTheDocument();
    fireEvent.keyDown(document, { code: "ArrowDown", key: "ArrowDown" });
    await waitFor(() =>
      expect(
        screen.getByText(
          viMessages.catalog.dnd.draggedOverTarget
            .replace("{name}", "Hàm số")
            .replace("{target}", "Tích phân"),
        ),
      ).toBeInTheDocument(),
    );
    fireEvent.keyDown(document, { code: "Space", key: " " });
    await waitFor(() =>
      expect(api.reorderItems).toHaveBeenCalledWith("grp-knowledge", {
        itemIds: [KNOWLEDGE_CH2.id, KNOWLEDGE_CH1.id],
      }),
    );
    expect(
      screen.getByText(
        viMessages.catalog.dnd.droppedOnTarget
          .replace("{name}", "Hàm số")
          .replace("{target}", "Tích phân"),
      ),
    ).toBeInTheDocument();
    rectSpy.mockRestore();
    viRender.unmount();

    // English locale gets the same localized announcements (message parity)
    const apiEn = createTreeMockApi();
    const enRender = renderCatalog({ api: apiEn, locale: "en", search: "group=KNOWLEDGE" });
    expect(await screen.findByText("Functions")).toBeInTheDocument();
    expect(screen.getByText(en.catalog.dnd.instructions)).toBeInTheDocument();
    enRender.unmount();
  });

  it("đẩy lỗi 401 từ exportItems, previewImport và moveItem tới MutationCache.onError", async () => {
    const onMutationError = vi.fn();
    const client = new QueryClient({
      mutationCache: new MutationCache({ onError: onMutationError }),
      defaultOptions: { queries: { retry: false } },
    });
    const err401 = new ApiStatusError(401, "Unauthorized");
    const api = createTreeMockApi({
      exportItems: vi.fn().mockRejectedValue(err401),
      previewImport: vi.fn().mockRejectedValue(err401),
      moveItem: vi.fn().mockRejectedValue(err401),
    });

    renderCatalog({ api, search: "group=KNOWLEDGE", client });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();

    // 1. exportItems 401 -> MutationCache.onError
    await userEvent.click(screen.getByRole("button", { name: "Xuất Excel" }));
    await waitFor(() => expect(onMutationError).toHaveBeenCalledTimes(1));

    // 2. previewImport 401 -> MutationCache.onError
    await userEvent.click(screen.getByRole("button", { name: "Nhập Excel" }));
    const importDlg = await screen.findByRole("dialog");
    const fileInput = within(importDlg).getByLabelText(/Tệp Excel/) as HTMLInputElement;
    const validXlsx = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], "tree.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await userEvent.upload(fileInput, validXlsx);
    await userEvent.click(within(importDlg).getByRole("button", { name: "Kiểm tra tệp" }));
    await waitFor(() => expect(onMutationError).toHaveBeenCalledTimes(2));
    await userEvent.click(within(importDlg).getByRole("button", { name: "Hủy" }));

    // 3. moveItem 401 -> MutationCache.onError
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByRole("button", { name: "Thao tác của Tính đơn điệu" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Chuyển nhánh" }));
    const moveDlg = await screen.findByRole("dialog");
    await userEvent.click(await within(moveDlg).findByRole("radio", { name: /Tích phân/ }));
    await userEvent.click(within(moveDlg).getByRole("button", { name: "Chuyển" }));
    await waitFor(() => expect(onMutationError).toHaveBeenCalledTimes(3));
  });

  it("hiển thị đúng nút cha khi sửa nút cây không phải gốc (R1-07)", async () => {
    const api = createTreeMockApi({
      getItem: vi.fn(async (id) => {
        if (id === KNOWLEDGE_CH1.id) return toTreeItemDetail(KNOWLEDGE_CH1);
        if (id === KNOWLEDGE_BAI1.id) return toTreeItemDetail(KNOWLEDGE_BAI1);
        return toTreeItemDetail(KNOWLEDGE_CH1);
      }),
    });
    renderCatalog({ api, search: "group=KNOWLEDGE" });

    await userEvent.click(await screen.findByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByRole("button", { name: "Thao tác của Tính đơn điệu" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Sửa" }));
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect(api.getItem).toHaveBeenCalledWith(KNOWLEDGE_CH1.id));
    expect(await within(dialog).findByText(KNOWLEDGE_CH1.code)).toBeInTheDocument();
    expect(within(dialog).getByText(KNOWLEDGE_CH1.nameVi)).toBeInTheDocument();
    expect(
      within(dialog).queryByText(viMessages.catalog.treeNodeDialog.rootParentLabel),
    ).not.toBeInTheDocument();
  });

  it("khóa Lưu và hiện Thử lại khi getItemUsages lỗi lúc đổi ACTIVE -> INACTIVE trong dialog sửa (R1-13)", async () => {
    const getItemUsages = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce({
        targetId: "item-toan",
        targetCode: "TOAN",
        totalCount: 0,
        blocking: false,
        usages: [],
      });
    const api = createMockApi({ getItemUsages });
    renderCatalog({ api, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Sửa Toán học" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Trạng thái/ }));
    await userEvent.click(await screen.findByRole("option", { name: "Ngừng sử dụng" }));

    expect(
      await within(dialog).findByText(viMessages.catalog.stopItemDialog.usagesLoadError),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Lưu" })).toBeDisabled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Thử lại" }));
    await waitFor(() =>
      expect(
        within(dialog).queryByText(viMessages.catalog.stopItemDialog.usagesLoadError),
      ).not.toBeInTheDocument(),
    );
    expect(within(dialog).getByRole("button", { name: "Lưu" })).not.toBeDisabled();
  });

  it("hiện lỗi và nút Thử lại khi getOptions thất bại trong CombinationFormDialog (R1-14)", async () => {
    const getOptions = vi
      .fn()
      .mockRejectedValueOnce(new Error("Options failed"))
      .mockRejectedValueOnce(new Error("Options failed"))
      .mockResolvedValueOnce(EXAM_OPTIONS)
      .mockResolvedValueOnce(SUBJECT_OPTIONS);
    const api = createMockApi({ getOptions });
    renderCatalog({ api, search: "group=SUBJECT_COMBINATION" });

    await userEvent.click(await screen.findByRole("button", { name: "Thêm giá trị" }));
    const dialog = await screen.findByRole("dialog");
    const errors = await within(dialog).findAllByText(
      viMessages.catalog.combinationDialog.optionsLoadError,
    );
    expect(errors.length).toBe(2);

    const retryButtons = within(dialog).getAllByRole("button", { name: "Thử lại" });
    await userEvent.click(retryButtons[0]!);
    await userEvent.click(retryButtons[1]!);
    await waitFor(() =>
      expect(
        within(dialog).queryByText(viMessages.catalog.combinationDialog.optionsLoadError),
      ).not.toBeInTheDocument(),
    );
  });

  it("khóa bỏ nhóm liên kết khi linkedItemCount > 0, hiển thị breadcrumb/value/jump/usages/questionScope ở pane 340px (R1-08)", async () => {
    const refGroupsWithLinked: CatalogGroupRefResponse[] = [
      ...SYSTEM_KNOWLEDGE_GROUP.refGroups,
      {
        refGroupId: "grp-grade",
        code: "GRADE",
        nameVi: "Khối lớp",
        nameEn: "Grade",
        status: "ACTIVE",
        isDefault: false,
        position: 1,
        linkedItemCount: 2,
      },
    ];
    const api = createTreeMockApi({
      listRefGroups: vi.fn(async () => refGroupsWithLinked),
      getItemUsages: vi.fn(async (id) => ({
        targetId: id,
        targetCode: "B1",
        totalCount: 4,
        blocking: false,
        usages: [
          {
            source: "Ngân hàng câu hỏi",
            objectType: "QUESTION",
            count: 4,
            blocking: false,
            sampleLabels: ["Q-1"],
          },
        ],
      })),
      getDescendants: vi.fn(async () => [
        {
          id: "node-kt1",
          code: "KT1",
          nameVi: "Cực trị",
          depth: 3,
          parentId: "node-b1",
          path: "/node-ch1/node-b1/node-kt1/",
          value: 4,
        },
      ]),
    });

    renderCatalog({ api, search: "group=KNOWLEDGE" });
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    await waitFor(() => expect(api.listRefGroups).toHaveBeenCalledWith("grp-knowledge"));

    // Non-default ref group with linkedItemCount > 0 has disabled remove button
    expect(
      await screen.findByRole("button", { name: "Bỏ danh mục liên kết Khối lớp" }),
    ).toBeDisabled();

    // Expand CH1 and select B1 -> detail pane shows breadcrumb, value, jump button, usages, question scope
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByText("Tính đơn điệu"));

    const detailPane = screen.getByRole("complementary", { name: "Chi tiết nút đang chọn" });
    const breadcrumbNav = within(detailPane).getByRole("navigation", {
      name: viMessages.catalog.tree.breadcrumbRoot,
    });
    expect(within(breadcrumbNav).getByRole("button", { name: "Hàm số" })).toBeInTheDocument();
    expect(within(detailPane).getByText("3")).toBeInTheDocument();
    expect(await within(detailPane).findByText("Ngân hàng câu hỏi")).toBeInTheDocument();
    expect(
      within(detailPane).getByText("Đề gắn nút này lấy câu hỏi của 1 kiến thức."),
    ).toBeInTheDocument();

    // Click inherited ref pill to jump to source ancestor CH1
    await userEvent.click(
      within(detailPane).getByRole("button", { name: "Đi tới CH1 — Hàm số" }),
    );
    await waitFor(() => {
      expect(within(detailPane).getByRole("heading", { name: "Hàm số" })).toBeInTheDocument();
    });
  });

  it("hiện trạng thái tải/lỗi/thử lại và chỉ đếm nút lá thành công cho phạm vi câu hỏi (R2-02)", async () => {
    const twoLeaves = [
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
      {
        id: "node-kt2",
        code: "KT2",
        nameVi: "GTLN GTNN",
        nameEn: "Max Min",
        depth: 3,
        parentId: "node-b1",
        path: "/node-ch1/node-b1/node-kt2/",
        value: 5,
      },
    ];
    const getDescendants = vi
      .fn()
      .mockRejectedValueOnce(new Error("descendants fail"))
      .mockResolvedValue(twoLeaves);
    const api = createTreeMockApi({ getDescendants });
    renderCatalog({ api, search: "group=KNOWLEDGE" });

    // Expand CH1 and select B1 (1 direct child, 2 leaf descendants on success)
    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    await userEvent.click(await screen.findByText("Tính đơn điệu"));

    const detailPane = await screen.findByRole("complementary", {
      name: "Chi tiết nút đang chọn",
    });

    // Rejected descendants -> localized error + retry, never the childCount fallback
    expect(
      await within(detailPane).findByText(viMessages.catalog.tree.questionScopeError),
    ).toBeInTheDocument();
    expect(
      within(detailPane).queryByText(/câu hỏi của 1 kiến thức/),
    ).not.toBeInTheDocument();

    await userEvent.click(within(detailPane).getByRole("button", { name: "Thử lại" }));

    // Retry succeeds -> definitive leaf count (2), not the direct-child count (1)
    await waitFor(() =>
      expect(
        within(detailPane).getByText("Đề gắn nút này lấy câu hỏi của 2 kiến thức."),
      ).toBeInTheDocument(),
    );
    expect(getDescendants).toHaveBeenCalledTimes(2);
  });

  it("giữ nguyên chữ hoa của tên cấp tùy chỉnh trong câu phạm vi câu hỏi (R2-03)", async () => {
    const cefrGroup: CatalogGroupResponse = {
      ...SYSTEM_KNOWLEDGE_GROUP,
      id: "grp-cefr",
      code: "CEFR_TREE",
      nameVi: "Cây CEFR",
      nameEn: "CEFR tree",
      type: "CUSTOM",
      system: false,
      levels: ["Band", "CEFR"],
      refGroups: [],
    };
    const cefrRoot: CatalogTreeNodeResponse = {
      ...KNOWLEDGE_CH1,
      id: "node-cefr-a1",
      groupId: "grp-cefr",
      code: "A1",
      nameVi: "Sơ cấp",
      nameEn: "Beginner",
      depth: 1,
      path: "/node-cefr-a1/",
      hasChildren: false,
      childCount: 0,
      value: 1,
    };
    const api = createTreeMockApi({
      listGroups: vi.fn(async () => pageOfGroups([cefrGroup])),
      listTree: vi.fn(async () => [cefrRoot]),
      getItem: vi.fn(async () => toTreeItemDetail(cefrRoot)),
      getDescendants: vi.fn(async () => []),
    });
    renderCatalog({ api, search: "group=CEFR_TREE&node=node-cefr-a1" });

    const detailPane = await screen.findByRole("complementary", {
      name: "Chi tiết nút đang chọn",
    });
    expect(
      await within(detailPane).findByText("Đề gắn nút này lấy câu hỏi của 0 CEFR."),
    ).toBeInTheDocument();
  });

  it("chặn hiển thị chi tiết khi ?node= thuộc danh mục khác (R1-10)", async () => {
    const api = createTreeMockApi({
      getItem: vi.fn(async () => ({
        ...SUBJECT_ITEMS[0]!,
        id: "item-toan",
        groupId: "grp-subject",
        groupCode: "SUBJECT",
      })),
    });
    renderCatalog({ api, search: "group=KNOWLEDGE&node=item-toan" });

    const detailPane = await screen.findByRole("complementary", { name: "Chi tiết nút đang chọn" });
    expect(
      await within(detailPane).findByText(viMessages.catalog.tree.nodeWrongGroup),
    ).toBeInTheDocument();
    expect(within(detailPane).queryByText("TOAN")).not.toBeInTheDocument();
    expect(within(detailPane).queryByRole("button", { name: "Sửa" })).not.toBeInTheDocument();
  });

  it("khóa nút thêm nút con trên nút đã ngừng hoặc nhóm cây đang ngừng sử dụng (R1-11)", async () => {
    const stoppedChapter: CatalogTreeNodeResponse = {
      ...KNOWLEDGE_CH1,
      status: "INACTIVE",
      effectiveStatus: "INACTIVE",
    };
    const api = createTreeMockApi({
      listTree: vi.fn(async () => [stoppedChapter]),
    });
    const { unmount } = renderCatalog({ api, search: "group=KNOWLEDGE" });

    expect(await screen.findByText("Hàm số")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thêm Bài vào Hàm số" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    unmount();

    const inactiveTreeGroup: CatalogGroupResponse = {
      ...SYSTEM_KNOWLEDGE_GROUP,
      status: "INACTIVE",
      itemCount: 0,
    };
    renderCatalog({
      api: createTreeMockApi({
        listGroups: vi.fn(async () => pageOfGroups([inactiveTreeGroup])),
        listTree: vi.fn(async () => []),
      }),
      search: "group=KNOWLEDGE",
    });
    const addButtons = await screen.findAllByRole("button", { name: "Thêm Chương" });
    for (const btn of addButtons) {
      expect(btn).toBeDisabled();
    }
  });

  it("hiện lỗi và nút Thử lại khi tải lười nút con hoặc chi tiết nút deep-link thất bại (R1-14)", async () => {
    const api = createTreeMockApi({
      listTree: vi
        .fn()
        .mockResolvedValueOnce([KNOWLEDGE_CH1, KNOWLEDGE_CH2])
        .mockRejectedValueOnce(new Error("Lazy fail"))
        .mockResolvedValueOnce([KNOWLEDGE_BAI1]),
      getItem: vi
        .fn()
        .mockRejectedValueOnce(new Error("Detail fail"))
        .mockResolvedValueOnce(toTreeItemDetail(KNOWLEDGE_CH1)),
    });
    renderCatalog({ api, search: "group=KNOWLEDGE&node=node-ch1" });

    const detailPane = await screen.findByRole("complementary", { name: "Chi tiết nút đang chọn" });
    expect(
      await within(detailPane).findByText(viMessages.catalog.tree.detailLoadError),
    ).toBeInTheDocument();
    await userEvent.click(within(detailPane).getByRole("button", { name: "Thử lại" }));
    await waitFor(() =>
      expect(
        within(detailPane).queryByText(viMessages.catalog.tree.detailLoadError),
      ).not.toBeInTheDocument(),
    );

    await userEvent.click(screen.getByRole("button", { name: "Mở Hàm số" }));
    expect(await screen.findByText(viMessages.catalog.tree.childrenLoadError)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Tính đơn điệu")).toBeInTheDocument();
  });
});

describe("CatalogView Slice 4: xuất và nhập Excel danh mục", { timeout: 20_000 }, () => {
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

  it("hiển thị đúng thông báo lỗi khi máy chủ trả về IMPORT_FILE_TOO_LARGE", async () => {
    const api = createTreeMockApi({
      previewImport: vi.fn(async () => {
        throw new CatalogApiError(400, "File too large", "IMPORT_FILE_TOO_LARGE");
      }),
    });
    renderCatalog({ api, search: "group=SUBJECT" });

    await userEvent.click(await screen.findByRole("button", { name: "Nhập Excel" }));
    const dialog = await screen.findByRole("dialog");
    const fileInput = within(dialog).getByLabelText(/Tệp Excel/) as HTMLInputElement;
    const validFile = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], "subject.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await userEvent.upload(fileInput, validFile);
    await userEvent.click(within(dialog).getByRole("button", { name: "Kiểm tra tệp" }));

    expect(
      await within(dialog).findByText(viMessages.catalog.errorCodes.IMPORT_FILE_TOO_LARGE),
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
            { rowNo: 5, column: "level2NameVi", code: "REQUIRED", message: "Thiếu tên Bài" },
            { rowNo: 6, column: "level1Code", code: "PARENT_REQUIRED", message: "Thiếu cấp cha" },
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

    // VIS-4: cột là tiêu đề trong tệp Excel, lỗi là câu theo mã; không lộ tên trường hay mã lỗi.
    const errorTable = await within(dialog).findByRole("table");
    const errorRows = within(errorTable).getAllByRole("row").slice(1);
    expect(errorRows).toHaveLength(3);
    expect(within(errorRows[0]!).getByText("Mã")).toBeInTheDocument();
    expect(
      within(errorRows[0]!).getByText(viMessages.catalog.errorCodes.CATALOG_CODE_TAKEN),
    ).toBeInTheDocument();
    expect(within(errorRows[1]!).getByText("Bài (Tiếng Việt)")).toBeInTheDocument();
    expect(
      within(errorRows[1]!).getByText(viMessages.catalog.errorCodes.REQUIRED),
    ).toBeInTheDocument();
    expect(within(errorRows[2]!).getByText("Mã Chương")).toBeInTheDocument();
    expect(
      within(errorTable).queryByText(
        /level\d|CATALOG_CODE_TAKEN|PARENT_REQUIRED|REQUIRED$|nameVi/,
      ),
    ).not.toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: /Nhập 2 dòng hợp lệ/ })).toBeDisabled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Tải tệp lỗi" }));
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

  it("R3-01: xem trước nhập cây hệ thống KNOWLEDGE ở locale en dùng Chapter/Lesson/Knowledge, không lộ tên cấp tiếng Việt", async () => {
    const api = createTreeMockApi({
      previewImport: vi.fn(async () => ({
        previewId: "prev-en",
        groupId: "grp-knowledge",
        groupCode: "KNOWLEDGE",
        groupKind: "TREE" as const,
        totalRows: 3,
        validRows: 0,
        errorRows: 3,
        canConfirm: false,
        errors: [
          { rowNo: 4, column: "level1Code", code: "PARENT_REQUIRED", message: "x" },
          { rowNo: 5, column: "level2NameVi", code: "REQUIRED", message: "x" },
          { rowNo: 6, column: "level3NameEn", code: "REQUIRED", message: "x" },
        ],
      })),
    });

    renderCatalog({ api, locale: "en", search: "group=KNOWLEDGE" });
    await userEvent.click(await screen.findByRole("button", { name: en.catalog.importExcel }));
    const dialog = await screen.findByRole("dialog");
    const file = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], "tree.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await userEvent.upload(within(dialog).getByLabelText(/Excel file/) as HTMLInputElement, file);
    await userEvent.click(
      within(dialog).getByRole("button", { name: en.catalog.importDialog.uploadButton }),
    );

    const errorTable = await within(dialog).findByRole("table");
    const rows = within(errorTable).getAllByRole("row").slice(1);
    expect(within(rows[0]!).getByText("Chapter code")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Lesson (Vietnamese)")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Knowledge (English)")).toBeInTheDocument();
    expect(within(errorTable).queryByText(/Chương|Bài|Kiến thức/)).not.toBeInTheDocument();
  });
});

describe("importDialog: số nhiều ICU cho số dòng (V2-5)", () => {
  const translators = {
    en: createTranslator({ locale: "en", messages: en, namespace: "catalog.importDialog" }),
    vi: createTranslator({ locale: "vi", messages: viMessages, namespace: "catalog.importDialog" }),
  };

  it("en: một dòng dùng số ít, nhiều dòng dùng số nhiều, số lớn có dấu phân cách", () => {
    const t = translators.en;
    expect(t("confirmButton", { count: 1 })).toBe("Import 1 valid row");
    expect(t("confirmButton", { count: 2 })).toBe("Import 2 valid rows");
    expect(t("confirmButton", { count: 1200 })).toBe("Import 1,200 valid rows");
    expect(t("validBanner", { count: 1 })).toBe("The row is valid and ready to import.");
    expect(t("validBanner", { count: 5 })).toBe("All 5 rows are valid and ready to import.");
    expect(t("errorFileBanner", { count: 1 })).toMatch(/^The file has 1 invalid row\. Fix it /);
    expect(t("errorFileBanner", { count: 3 })).toMatch(/^The file has 3 invalid rows\. Fix them /);
  });

  it("vi: câu giữ nguyên cho mọi số lượng", () => {
    const t = translators.vi;
    expect(t("confirmButton", { count: 1 })).toBe("Nhập 1 dòng hợp lệ");
    expect(t("confirmButton", { count: 3 })).toBe("Nhập 3 dòng hợp lệ");
    expect(t("validBanner", { count: 1 })).toBe("Tất cả 1 dòng đều hợp lệ và sẵn sàng nhập.");
    expect(t("errorFileBanner", { count: 2 })).toMatch(/^Tệp có 2 dòng lỗi\./);
  });
});

describe("CatalogView: dịch cấp cây hệ thống và pickName theo useLocale()", { timeout: 20_000 }, () => {
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
    await userEvent.click(screen.getByRole("button", { name: "Actions for Monotonicity" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Move branch" }));
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

  it("dịch đầy đủ mã lỗi dòng Excel, nơi đang dùng, fallback fieldErrors, hành động audit và ngày giờ qua next-intl ở locale en", async () => {
    const createObjectUrlSpy = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:mock-url");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);

    const api = createTreeMockApi({
      deleteItem: vi.fn(async () => {
        throw new CatalogApiError(409, "In use", "CATALOG_ITEM_IN_USE", [
          { field: "id", code: "CATALOG_ITEM_IN_USE", message: "Mã tổ hợp (1): QĐ01" },
        ]);
      }),
      getItemUsages: vi
        .fn()
        .mockResolvedValueOnce({
          targetId: "item-toan",
          targetCode: "TOAN",
          totalCount: 2,
          blocking: false,
          usages: [
            {
              source: "Mã tổ hợp",
              objectType: "SUBJECT_COMBINATION",
              count: 1,
              blocking: false,
              sampleLabels: ["QD01"],
            },
            {
              source: "Danh mục cây",
              objectType: "TREE_NODE",
              count: 1,
              blocking: false,
              sampleLabels: ["CH1"],
            },
          ],
        })
        .mockResolvedValueOnce({
          targetId: "item-toan",
          targetCode: "TOAN",
          totalCount: 0,
          blocking: false,
          usages: [],
        }),
      listItemAuditEvents: vi.fn(async (itemId: string) => ({
        items: [
          {
            id: "audit-reorder",
            action: "CATALOG_ITEM_REORDERED",
            actorType: "USER" as const,
            actorUsername: "content.admin",
            actorRoles: ["ADMIN"],
            module: "catalog",
            occurredAt: "2026-11-14T01:00:00Z",
            outcome: "SUCCESS" as const,
            targetId: itemId,
            targetType: "CATALOG_ITEM",
          },
        ],
        page: 0,
        size: 10,
        totalItems: 1,
        totalPages: 1,
      })),
      previewImport: vi.fn(async () => ({
        previewId: "prev-en-err",
        groupId: "grp-subject",
        groupCode: "SUBJECT",
        groupKind: "SIMPLE" as const,
        totalRows: 3,
        validRows: 0,
        errorRows: 3,
        canConfirm: false,
        errors: [
          { rowNo: 2, column: "code", code: "REQUIRED", message: "Thiếu mã" },
          { rowNo: 3, column: "code", code: "CODE_INVALID", message: "Mã sai" },
          {
            rowNo: 4,
            column: "code",
            code: "CATALOG_CODE_DUPLICATE_IN_FILE",
            message: "Trùng mã",
          },
        ],
      })),
    });

    renderCatalog({
      api,
      locale: "en",
      permissions: ["CATALOG.VIEW", "CATALOG.MODIFY", "AUDIT.VIEW"],
      search: "group=SUBJECT",
    });

    // 1. ItemDetailDrawer usages, audit (CATALOG_ITEM_REORDERED), and dd/MM/yyyy date formatting
    await userEvent.click(await screen.findByRole("button", { name: "Details for Mathematics" }));
    const drawer = await screen.findByRole("dialog");
    expect(within(drawer).getAllByText(/14\/11\/2026/).length).toBeGreaterThan(0);

    await userEvent.click(within(drawer).getByRole("tab", { name: "Usages" }));
    expect(await within(drawer).findByText("Subject combinations")).toBeInTheDocument();
    expect(within(drawer).getByText("Tree catalogs")).toBeInTheDocument();
    expect(
      within(drawer).queryByText(/Mã tổ hợp|Danh mục cây|SUBJECT_COMBINATION|TREE_NODE/),
    ).not.toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole("tab", { name: "Change history" }));
    expect(await within(drawer).findByText("Reordered values")).toBeInTheDocument();
    expect(within(drawer).getByText(/14\/11\/2026/)).toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole("button", { name: "Close" }));

    // 2. DeleteInUseDialog fallback fieldErrors localized (not raw fe.message)
    await userEvent.click(screen.getByRole("button", { name: "Delete Mathematics" }));
    const confirmDlg = await screen.findByRole("dialog");
    await userEvent.type(within(confirmDlg).getByLabelText(/Reason for deletion/), "Cleanup");
    await userEvent.click(within(confirmDlg).getByRole("button", { name: "Delete value" }));
    const inUseDlg = await screen.findByRole("dialog");
    expect(
      await within(inUseDlg).findByText(en.catalog.errorCodes.CATALOG_ITEM_IN_USE),
    ).toBeInTheDocument();
    expect(within(inUseDlg).queryByText(/Mã tổ hợp/)).not.toBeInTheDocument();
    await userEvent.click(within(inUseDlg).getAllByRole("button", { name: "Close" })[0]!);

    // 3. Excel row errors + localized CSV export in en
    await userEvent.click(screen.getByRole("button", { name: "Import Excel" }));
    const importDlg = await screen.findByRole("dialog");
    const fileInput = within(importDlg).getByLabelText(/Excel file/) as HTMLInputElement;
    const validXlsx = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], "subject.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await userEvent.upload(fileInput, validXlsx);
    await userEvent.click(within(importDlg).getByRole("button", { name: "Validate file" }));

    expect(await within(importDlg).findByText(en.catalog.errorCodes.REQUIRED)).toBeInTheDocument();
    expect(within(importDlg).getByText(en.catalog.errorCodes.CODE_INVALID)).toBeInTheDocument();
    expect(
      within(importDlg).getByText(en.catalog.errorCodes.CATALOG_CODE_DUPLICATE_IN_FILE),
    ).toBeInTheDocument();
    expect(within(importDlg).queryByText(/Thiếu mã|Mã sai|Trùng mã/)).not.toBeInTheDocument();

    await userEvent.click(within(importDlg).getByRole("button", { name: "Download error report" }));
    expect(createObjectUrlSpy).toHaveBeenCalled();
    const exportedBlob = createObjectUrlSpy.mock.calls.at(-1)?.[0] as Blob;
    const { promise: csvPromise, resolve: resolveCsv } = Promise.withResolvers<string>();
    const reader = new FileReader();
    reader.onload = () => resolveCsv(String(reader.result ?? ""));
    reader.readAsText(exportedBlob);
    const csvText = await csvPromise;
    expect(csvText).toContain(en.catalog.errorCodes.REQUIRED);
    expect(csvText).not.toContain("Thiếu mã");
    vi.restoreAllMocks();
  });
});
