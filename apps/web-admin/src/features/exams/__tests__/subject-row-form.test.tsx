import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { boot, openStructure, putCalls, requestLog, sectionsOf, wireBlock, wireSection, type Wire } from "./subject-form-harness";
import { bcaLiteratureSection, hsaLiteratureSection, oneSection, qdaLiteratureSection, thptqgLiteratureSection, thptqgPhysicsParts } from "./subject-row-builders";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-3 lát CR-3a, Danh sách dòng thật (`row-list-form.tsx`) trên `StructureEditor` thật: cờ `enabled` của HSA, QDA, BCA (VAN, ANH) và THPTQG đã bật
 * ở sản phẩm, nên các ca này không cần chỗ cắm cờ; cặp của lát CR-3b (HSCA, V-SAT, SP2E) chỉ bật bằng `patchSubjectFormEntry` để kiểm khóa.
 */
const restores: Array<() => void> = [];
afterEach(() => {
  while (restores.length) restores.pop()!();
});

const rowNames = () => Array.from(document.querySelectorAll<HTMLElement>("[data-form-row]")).map((row) => row.querySelector("[data-exam-content]")?.textContent);
const rowOf = (id: string) => document.querySelector<HTMLElement>(`[data-form-row="${id}"]`)!;

describe("Danh sách dòng: bảng dòng của HSA, QDA", () => {
  it("hsaLiteratureRows: HSA Văn hiện 5 dòng đúng thứ tự mẫu; nhóm 5 hiện 5 chùm con có loại văn bản; công tắc đảo ở nhóm 5", async () => {
    const user = userEvent.setup();
    boot({ kind: "HSA", parts: oneSection(hsaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(rowNames()).toEqual(["Tu khac nhom nghia", "Tu sai", "Cap tu dien", "Mot ngu lieu mot cau", "Doc hieu"]);
    const slots = within(document.querySelector<HTMLElement>('[data-form-slots="van-5"]')!).getAllByRole("listitem");
    expect(slots).toHaveLength(5);
    expect(slots[0]).toHaveTextContent("Chùm 1: VAN_NGHI_LUAN_XA_HOI");
    expect(slots[4]).toHaveTextContent("Chùm 5: VAN_VAN_XUOI");
    // Nút thêm, xóa, lên, xuống dòng không có (R-13); chỉ có nút Sửa.
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa|Lên|Xuống|Tách/ })).toBeNull();
    // Công tắc đảo chỉ có ở nhóm 5.
    await user.click(within(rowOf("van-5")).getByRole("button", { name: /Sửa dòng Doc hieu/ }));
    expect(await screen.findByRole("switch", { name: "Đảo thứ tự các chùm" })).toBeChecked();
    await user.click(within(rowOf("van-1")).getByRole("button", { name: /Sửa dòng Tu khac nhom nghia/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Tu khac nhom nghia/ });
    expect(screen.queryByRole("switch", { name: "Đảo thứ tự các chùm" })).toBeNull();
  });

  it("qdaLiteratureRowsAndSlots: QDA Văn đúng 5 dòng; dòng 5 là một nhóm 5 chùm × 5 có nhãn từng chùm và một công tắc đảo đổi được", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(rowNames()).toHaveLength(5);
    expect(rowOf("van-5")).toHaveTextContent("Câu chùm");
    expect(rowOf("van-5")).toHaveTextContent("5 chùm × 5 (25 câu)");
    const slots = document.querySelectorAll('[data-form-slots="van-5"] [data-form-slot]');
    expect(Array.from(slots).map((slot) => slot.textContent)).toEqual([
      "Chùm 1: VAN_THONG_TIN_NGHI_LUAN",
      "Chùm 2: VAN_NGHI_LUAN",
      "Chùm 3: VAN_THO",
      "Chùm 4: VAN_THIEU_NHI",
      "Chùm 5: VAN_TRUYEN",
    ]);
    await user.click(within(rowOf("van-5")).getByRole("button", { name: /Sửa dòng Doc hieu/ }));
    const toggle = await screen.findByRole("switch", { name: "Đảo thứ tự các chùm" });
    await user.click(toggle);
    expect(toggle).not.toBeChecked();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const block = sectionsOf(putCalls(server)[0]!.body)["sec-van"]!.blocks as Wire[];
    expect(block.find((item) => item.id === "van-5")).toMatchObject({ shuffleUnits: false, slots: expect.arrayContaining([expect.objectContaining({ index: 1 })]) });
    expect(block.find((item) => item.id === "van-1")).not.toHaveProperty("shuffleUnits", false);
  });
});

describe("Danh sách dòng: chỉ số chỉ đọc, tự luận", () => {
  it("readOnlyFigures: dòng THPTQG 4 câu Đ/S hiện 16 ý và thang 4 điểm; cách chấm dự kiến của mẫu còn", async () => {
    const user = userEvent.setup();
    boot({ kind: "THPTQG", parts: thptqgPhysicsParts() });
    openStructure("?section=sec-p2");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(within(rowOf("p2-tf")).getByText("4 câu Đ/S · 16 ý · thang 4 điểm")).toBeInTheDocument();
    await user.click(within(rowOf("p2-tf")).getByRole("button", { name: /Sửa dòng Dung sai/ }));
    expect(await screen.findByText(/Theo mẫu: .*4 ý.*1 điểm mỗi câu.*bảng điểm theo số ý đúng 0,1 · 0,25 · 0,5 · 1/)).toBeInTheDocument();
    // Số câu, ý và điểm do mẫu đặt: không có ô sửa điểm hay hình dạng câu (R-13).
    expect(screen.queryByRole("textbox", { name: /Điểm mỗi câu|Số ý/ })).toBeNull();
  });

  it("essayCases: THPTQG Văn không có ô sửa; BCA Văn một dòng tự luận sửa được MAPID", async () => {
    const user = userEvent.setup();
    boot({ kind: "THPTQG", parts: oneSection(thptqgLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.getByText("Tự luận – tạm bỏ qua. Phần này không có ô sửa.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa dòng/ })).toBeNull();
    expect(rowNames()).toEqual(["Tu luan"]);
    window.history.replaceState(null, "", "/");
    // BCA: một dòng tự luận, sửa được; Thêm điều kiện có MAPID (lọc).
    boot({ kind: "BCA", parts: oneSection(bcaLiteratureSection()) });
    const rendered = openStructure("?section=sec-van");
    await rendered.findAllByRole("heading", { name: "Các dòng lấy câu" });
    await user.click((await screen.findAllByRole("button", { name: /Sửa dòng Nghi luan xa hoi/ })).at(-1)!);
    await user.click(await screen.findByRole("button", { name: "Thêm điều kiện" }));
    expect(await screen.findByRole("menuitem", { name: /MAPID \(lọc\)/ })).toBeInTheDocument();
  });
});

const wireTemplate = (code: string, fields: Wire = {}, blocks: Wire[] = []): Wire => ({
  active: true,
  scope: "SUBJECT",
  timing: "SHARED",
  buildMode: "STANDARD",
  isDefault: true,
  publishable: true,
  fixedCounts: false,
  fixedBlockCounts: false,
  omittedLocked: false,
  kindCode: "HSCA",
  maxScore: 10,
  ...fields,
  code,
  structure: { parts: [{ id: null, order: 1, name: "Phan", durationMinutes: null, questionCount: 0, participation: { mode: "ALL" }, mode: "FIXED", sections: [{ id: null, subjectCode: "VAN", questionCount: 0, blocks }] }] },
});

const readOnlyCount = () => screen.getByRole("textbox", { name: /Số câu cần lấy/ });

describe("Danh sách dòng: khóa theo nguồn (R-7)", () => {
  const hscaMath = (count = 25): Wire[] => oneSection(wireSection("sec-toan", "TOAN", [wireBlock("toan-b1", { name: "Trac nghiem", questionCount: count, templateGroupCode: "HSCA_TOAN_B1" })]));

  it("locksBySource: khóa dòng (H42) chỉ ở bài SUBJECT từ mẫu fixedBlockCounts, bài PART cùng mẫu sửa được", async () => {
    const user = userEvent.setup();
    restores.push(patchSubjectFormEntry("HSCA", "TOAN", { enabled: true }));
    const templates = [wireTemplate("HSCA_TOAN", { fixedBlockCounts: true })];
    boot({ kind: "HSCA", parts: hscaMath(), templates, examOverrides: { scope: "SUBJECT", templateCode: "HSCA_TOAN" } });
    const subject = openStructure("?section=sec-toan");
    await subject.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.getAllByText("Theo mẫu đề chuẩn, không đổi số lượng").length).toBeGreaterThan(0);
    await user.click(within(rowOf("toan-b1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Trac nghiem/ });
    expect(readOnlyCount()).toHaveAttribute("readonly");
    expect(screen.getByRole("radio", { name: "Câu chùm" })).toBeDisabled();
    subject.unmount();

    // Bài PART cắt từ cùng mẫu: cùng cặp, cùng mẫu, sửa được.
    boot({ kind: "HSCA", parts: hscaMath(), templates, examOverrides: { scope: "PART", templateCode: "HSCA_TOAN" } });
    const part = openStructure("?section=sec-toan");
    await part.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.queryByText("Theo mẫu đề chuẩn, không đổi số lượng")).toBeNull();
    await user.click(within(rowOf("toan-b1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Trac nghiem/ });
    expect(readOnlyCount()).not.toHaveAttribute("readonly");
    expect(screen.getByRole("radio", { name: "Câu chùm" })).toBeEnabled();
  });

  it("locksBySource: khóa tổng (H27) khóa số câu của mục ở đầu môn nhưng dòng vẫn sửa được", async () => {
    const user = userEvent.setup();
    restores.push(patchSubjectFormEntry("HSCA", "TOAN", { enabled: true }));
    boot({ kind: "HSCA", parts: hscaMath(), templates: [wireTemplate("HSCA_TOAN", { fixedCounts: true })], examOverrides: { scope: "SUBJECT", templateCode: "HSCA_TOAN" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.getByText("Số câu của mục theo mẫu, không đổi")).toBeInTheDocument();
    await user.click(within(rowOf("toan-b1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Trac nghiem/ });
    expect(readOnlyCount()).not.toHaveAttribute("readonly");
  });

  it("locksBySource: khóa dạng (answerTypeLocked) khóa Cách lấy kèm lý do; số lượng vẫn sửa được", async () => {
    const user = userEvent.setup();
    boot({ kind: "THPTQG", parts: thptqgPhysicsParts() });
    openStructure("?section=sec-p2");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(within(rowOf("p2-tf")).getByText("Dạng câu do mẫu đặt, không đổi")).toBeInTheDocument();
    await user.click(within(rowOf("p2-tf")).getByRole("button", { name: /Sửa dòng Dung sai/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Dung sai/ });
    expect(screen.getByRole("radio", { name: "Câu chùm" })).toBeDisabled();
    expect(readOnlyCount()).not.toHaveAttribute("readonly");
  });

  it("locksBySource: khóa nhóm đã lược (H43, H49) không có công tắc bật lại; nhóm lược của SP2E không khóa", async () => {
    const user = userEvent.setup();
    restores.push(patchSubjectFormEntry("HSCA", "VAN", { enabled: true }), patchSubjectFormEntry("SP2E", "VAN", { enabled: true }));
    const writing = wireBlock("write-1", { name: "Viet", questionType: "ESSAY", questionCount: 1, omitted: true, templateGroupCode: "VAN_WRITE" });
    const writingPart = (): Wire[] => oneSection(wireSection("sec-van", "VAN", [writing], { questionCount: 0 }));
    const omittedTemplate = (kindCode: string, omittedLocked: boolean) => wireTemplate(`${kindCode}_VAN`, { kindCode, omittedLocked }, [{ ...writing, id: null }]);

    boot({ kind: "HSCA", parts: writingPart(), templates: [omittedTemplate("HSCA", true)], examOverrides: { scope: "SUBJECT", templateCode: "HSCA_VAN" } });
    const locked = openStructure("?section=sec-van");
    await locked.findByRole("heading", { name: "Các dòng lấy câu" });
    await user.click(within(rowOf("write-1")).getByRole("button", { name: /Sửa dòng Viet/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Viet/ });
    expect(screen.getAllByText("Nhóm đã lược theo mẫu, không bật lại").length).toBeGreaterThan(0);
    expect(screen.queryByRole("switch", { name: "Tạm bỏ qua" })).toBeNull();
    locked.unmount();

    boot({ kind: "SP2E", parts: writingPart(), templates: [omittedTemplate("SP2E", false)], examOverrides: { scope: "SUBJECT", templateCode: "SP2E_VAN" } });
    const free = openStructure("?section=sec-van");
    await free.findByRole("heading", { name: "Các dòng lấy câu" });
    await user.click(within(rowOf("write-1")).getByRole("button", { name: /Sửa dòng Viet/ }));
    const toggle = await screen.findByRole("switch", { name: "Tạm bỏ qua" });
    expect(toggle).toBeEnabled();
    expect(toggle).toBeChecked();
  });
});

const matrixWith = (over: Wire): Wire => ({ mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [], ...over });
const referenceBlock = () => document.querySelector<HTMLElement>("[data-reference-block]")!;
const openReference = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(within(referenceBlock()).getByText("Phân bổ đối chiếu"));
  return referenceBlock();
};

describe("Danh sách dòng: ẩn khối lớp (R-11)", () => {
  const subjectWithGrade = (id: string, code: string, extra: Wire = {}): Wire[] =>
    oneSection(
      wireSection(
        id,
        code,
        [wireBlock(`${id}-b1`, { name: "Trac nghiem", questionCount: 20 })],
        { matrix: matrixWith({ gradeEnforcement: "REFERENCE", byGrade: [{ code: "GRADE_10", value: 5 }, { code: "GRADE_11", value: 15 }], byDifficulty: [{ code: "RECOGNITION", value: 20 }], ...extra }) },
      ),
    );
  // HSA-C chuyển phân bố lớp Tham khảo có số sang Bắt buộc khi nạp (ngoại lệ chờ chuyên gia là bảng lớp Tiếng Anh chưa có số): HSA Anh dùng bảng lớp trống.
  const cases: ReadonlyArray<readonly [string, string, boolean, Wire]> = [
    ["HSA", "ANH", true, { byGrade: [] }],
    ["THPTQG", "SU", true, {}],
    ["THPTQG", "DIA", true, {}],
    ["THPTQG", "ECON_LAW", true, {}],
    ["THPTQG", "GDKTPL", true, {}],
    ["THPTQG", "ANH", true, {}],
    ["THPTQG", "TOAN", false, {}],
    ["QDA", "ANH", false, {}],
    ["BCA", "VAN", false, {}],
  ];

  it.each(cases)("hiddenGradeTable: %s %s ẩn khối lớp ở Phân bổ đối chiếu = %s", async (kind, subject, hidden, extra) => {
    const user = userEvent.setup();
    boot({ kind, parts: subjectWithGrade("sec-x", subject, extra) });
    const rendered = openStructure("?section=sec-x");
    await rendered.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    expect(within(reference).getByRole("heading", { name: /Độ khó/ })).toBeInTheDocument();
    expect(within(reference).queryByRole("heading", { name: /Khối lớp/ }) === null).toBe(hidden);
    // Thêm điều kiện của dòng: Khối lớp cũng ẩn.
    await user.click(within(rowOf("sec-x-b1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await user.click(await screen.findByRole("button", { name: "Thêm điều kiện" }));
    expect(screen.queryAllByRole("menuitem", { name: /^Khối lớp/ }).length === 0).toBe(hidden);
  });

  it("hiddenGradeKeepsData: lưu sau khi sửa độ khó giữ nguyên dữ liệu lớp của thân PUT", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "THPTQG", parts: subjectWithGrade("sec-x", "SU") });
    openStructure("?section=sec-x");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    const cell = within(reference).getAllByRole("textbox", { name: /Độ khó: .*số câu/ })[0]!;
    await user.clear(cell);
    await user.type(cell, "18");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = sectionsOf(putCalls(server)[0]!.body)["sec-x"]!.matrix as Wire;
    expect(saved.byGrade).toEqual([{ code: "GRADE_10", value: 5 }, { code: "GRADE_11", value: 15 }]);
    expect(saved.gradeEnforcement).toBe("REFERENCE");
  });

  it("hiddenGradeOpensDefault: điều kiện lớp đang Bắt buộc ở chỗ ẩn mở MD, giữ ràng buộc", async () => {
    boot({ kind: "THPTQG", parts: subjectWithGrade("sec-x", "SU", { gradeEnforcement: "REQUIRED" }) });
    openStructure("?section=sec-x");
    expect(await screen.findByText(/Môn này đang dùng thiết lập đầy đủ/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Các dòng lấy câu" })).toBeNull();
  });
});

describe("Danh sách dòng: số request, trường đã dọn (R-13)", () => {
  it("noExtraRequestsOnOpen: mở DS thật không gọi nhiều request hơn MD của cùng môn, cùng fixture", async () => {
    const open = async (enabled: boolean) => {
      const restore = patchSubjectFormEntry("QDA", "VAN", { enabled });
      try {
        const { server } = boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
        const rendered = openStructure("?section=sec-van");
        await rendered.findByRole("heading", { name: enabled ? "Các dòng lấy câu" : "Nhóm lấy câu" });
        await waitFor(() => expect(server.requests.length).toBeGreaterThan(0));
        const log = requestLog(server);
        rendered.unmount();
        return log;
      } finally {
        restore();
      }
    };
    const md = await open(false);
    const rows = await open(true);
    const count = (log: string[], request: string) => log.filter((item) => item === request).length;
    for (const request of new Set(rows)) expect(count(rows, request), request).toBeLessThanOrEqual(count(md, request));
  });

  it("noRemovedFields: DS không hiện trường nào ở bảng dọn (R-13) ở bảng, màn sửa dòng hay Phân bổ đối chiếu; MD vẫn hiện", async () => {
    const user = userEvent.setup();
    boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await user.click(within(rowOf("van-5")).getByRole("button", { name: /Sửa dòng Doc hieu/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Doc hieu/ });
    await openReference(user);
    const removed = [
      /Thêm dạng/,
      /Khai chỉ tiêu/,
      /Bỏ chỉ tiêu/,
      /Kết hợp/,
      /Riêng từng chiều/,
      /Lấy phần còn lại/,
      /Xem theo số câu của môn/,
      /Xóa nhóm/,
      /Nguồn$/,
    ];
    for (const pattern of removed) {
      expect(screen.queryAllByRole("button", { name: pattern }), String(pattern)).toHaveLength(0);
      expect(screen.queryAllByRole("radio", { name: pattern }), String(pattern)).toHaveLength(0);
      expect(screen.queryAllByRole("checkbox", { name: pattern }), String(pattern)).toHaveLength(0);
    }
    expect(screen.queryByRole("columnheader", { name: /Quy đổi|Còn thiếu/ })).toBeNull();
    expect(screen.queryByRole("region", { name: /Dạng trả lời/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /^(Đưa nhóm .* (lên|xuống)|Xóa nhóm)/ })).toBeNull();
  });

  it("noRemovedFields: MD của cùng môn vẫn hiện đủ các trường đó", async () => {
    restores.push(patchSubjectFormEntry("QDA", "VAN", { enabled: false }));
    boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Khai chỉ tiêu" }).length).toBeGreaterThan(0);
  });
});
