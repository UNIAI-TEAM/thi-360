import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
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

afterEach(() => routerMock.push.mockReset());

function qdaLiteratureMdSection(): Wire {
  const sec = qdaLiteratureSection();
  const blocks = [...(sec.blocks as Wire[])];
  blocks[0] = { ...blocks[0], childConditions: [{ from: 1, to: 1, nodeIds: ["n1"] }] };
  return { ...sec, blocks };
}

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
    const slotsRow = document.querySelector<HTMLElement>('[data-form-slots="van-5"] td[colspan]')!;
    expect(slotsRow).toHaveAttribute("colspan", "4");
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
    const slotsRow = document.querySelector<HTMLElement>('[data-form-slots="van-5"] td[colspan]')!;
    expect(slotsRow).toHaveAttribute("colspan", "4");
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
    expect(rowOf("p2-tf").querySelector("[data-row-figures]")).toHaveTextContent("4 câu Đ/S · 16 ý · thang 4 điểm");
    expect(within(rowOf("p2-tf")).getByText("Câu đơn · Đúng/Sai")).toBeInTheDocument();
    const headers = within(document.querySelector<HTMLElement>("[data-rows-card]")!).getAllByRole("columnheader");
    expect(headers).toHaveLength(5);
    expect(headers[1]).toHaveClass("min-w-44");
    await user.click(within(rowOf("p2-tf")).getByRole("button", { name: /Sửa dòng Dung sai/ }));
    expect(await screen.findByText(/Theo mẫu: .*4 ý.*1 điểm mỗi câu.*bảng điểm theo số ý đúng 0,1 · 0,25 · 0,5 · 1/)).toBeInTheDocument();
    // Số câu, ý và điểm do mẫu đặt: không có ô sửa điểm hay hình dạng câu (R-13).
    expect(screen.queryByRole("textbox", { name: /Điểm mỗi câu|Số ý/ })).toBeNull();
  });

  it("readOnlyFigures: bỏ qua thẻ data-row-figures khi dòng không có thang điểm hay số ý riêng", async () => {
    boot({ kind: "QDA", parts: oneSection(wireSection("sec-van", "VAN", [wireBlock("no-pts", { name: "Dong khong diem", questionCount: 5, requiredPointsPerQuestion: null })])) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(rowOf("no-pts").querySelector("[data-row-figures]")).toBeNull();
  });

  it("essayCases: THPTQG Văn không có ô sửa; BCA Văn một dòng tự luận sửa được MAPID", async () => {
    const user = userEvent.setup();
    boot({ kind: "THPTQG", parts: oneSection(thptqgLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.getByText("Tự luận – tạm bỏ qua. Phần này không có ô sửa.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa dòng/ })).toBeNull();
    expect(rowNames()).toEqual(["Tu luan"]);
    expect(within(document.querySelector<HTMLElement>("[data-rows-card]")!).getAllByRole("columnheader")).toHaveLength(4);
    expect(rowOf("essay-1").querySelector("[data-row-figures]")).toHaveTextContent("1 câu · 1 điểm");
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

const readOnlyCount = () => screen.getByRole("textbox", { name: /^(Số câu cần lấy|Số câu)$/ });

describe("Danh sách dòng: khóa theo nguồn (R-7)", () => {
  const hscaMath = (count = 25): Wire[] => oneSection(wireSection("sec-toan", "TOAN", [wireBlock("toan-b1", { name: "Trac nghiem", questionCount: count, templateGroupCode: "HSCA_TOAN_B1" })]));

  it.each(["HSCA", "VSAT"])("locksBySource: %s SUBJECT khóa dòng, PART cùng mẫu sửa được", async (kind) => {
    const user = userEvent.setup();
    const templates = [wireTemplate("EXAM_TOAN", { kindCode: kind, fixedBlockCounts: true })];
    boot({ kind, parts: hscaMath(), templates, examOverrides: { scope: "SUBJECT", templateCode: "EXAM_TOAN" } });
    const subject = openStructure("?section=sec-toan");
    await subject.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.getAllByText("Theo mẫu đề chuẩn, không đổi số lượng").length).toBeGreaterThan(0);
    await user.click(within(rowOf("toan-b1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await screen.findByRole("heading", { name: /Sửa dòng: Trac nghiem/ });
    expect(readOnlyCount()).toHaveAttribute("readonly");
    expect(screen.getByRole("radio", { name: "Câu chùm" })).toBeDisabled();
    subject.unmount();

    // Bài PART cắt từ cùng mẫu: cùng cặp, cùng mẫu, sửa được.
    boot({ kind, parts: hscaMath(), templates, examOverrides: { scope: "PART", templateCode: "EXAM_TOAN" } });
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

  it.each(["HSCA", "VSAT"])("locksBySource: nhóm Viết %s đã lược không bật lại; SP2E không khóa", async (kind) => {
    const user = userEvent.setup();
    const writing = wireBlock("write-1", { name: "Viet", questionType: "ESSAY", questionCount: 1, omitted: true, templateGroupCode: "VAN_WRITE" });
    const writingPart = (): Wire[] => oneSection(wireSection("sec-van", "VAN", [writing], { questionCount: 0 }));
    const omittedTemplate = (kindCode: string, omittedLocked: boolean) => wireTemplate(`${kindCode}_VAN`, { kindCode, omittedLocked }, [{ ...writing, id: null }]);

    boot({ kind, parts: writingPart(), templates: [omittedTemplate(kind, true)], examOverrides: { scope: "SUBJECT", templateCode: `${kind}_VAN` } });
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
    ["SP2E", "SU", true, {}],
    ["SP2E", "ECON_LAW", true, {}],
    ["SP2E", "GDKTPL", true, {}],
    ["SPT", "SU", true, {}],
    ["SPT", "ECON_LAW", true, {}],
    ["SPT", "GDKTPL", true, {}],
    ["VSAT", "DIA", true, {}],
    ["VSAT", "ANH", true, {}],
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

  it("hiddenGradeTopicQuota: SP2E:SU thêm chỉ tiêu chủ đề ở cả RowEditor và ReferenceBlock không có cột Giới hạn lớp (R1-4)", async () => {
    const user = userEvent.setup();
    boot({ kind: "SP2E", parts: oneSection(wireSection("sec-su", "SU", [wireBlock("su-1", { name: "Trac nghiem", questionCount: 25 })])) });
    openStructure("?section=sec-su");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });

    // Trong RowEditor:
    await user.click(within(rowOf("su-1")).getByRole("button", { name: /Sửa dòng Trac nghiem/ }));
    await user.click(await screen.findByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: /^Phân bổ theo MAPID/ }));
    const editorQuota = document.querySelector<HTMLElement>('[data-condition="topicQuotas"]')!;
    await user.click(within(editorQuota).getByRole("button", { name: /Thêm chỉ tiêu chủ đề/ }));
    expect(within(editorQuota).getByRole("columnheader", { name: "Giới hạn độ khó" })).toBeInTheDocument();
    expect(within(editorQuota).queryByRole("columnheader", { name: "Giới hạn lớp" })).toBeNull();
    expect(within(editorQuota).queryByText("Mọi lớp")).toBeNull();
    expect(within(editorQuota).queryByRole("checkbox", { name: /Lớp 10|Lớp 11|Lớp 12/ })).toBeNull();

    // Trong ReferenceBlock:
    const ref = await openReference(user);
    await user.click(within(ref).getByRole("button", { name: /Thêm chỉ tiêu chủ đề/ }));
    expect(within(ref).getByRole("columnheader", { name: "Giới hạn độ khó" })).toBeInTheDocument();
    expect(within(ref).queryByRole("columnheader", { name: "Giới hạn lớp" })).toBeNull();
    expect(within(ref).queryByText("Mọi lớp")).toBeNull();
    expect(within(ref).queryByRole("checkbox", { name: /Lớp 10|Lớp 11|Lớp 12/ })).toBeNull();
  });
});

describe("Danh sách dòng: phân bổ đối chiếu CR-3b", () => {
  it("promptStaysReference: phân bố 67 lệnh và nhiều môn khóa, không đổi đơn vị khi lưu", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { name: "Trac nghiem", questionCount: 25 })]));
    const { server, state } = boot({ kind: "VSAT", parts: rows });
    state.examQuotas = [
      { id: "q1", name: "Theo lenh", dimension: "GRADE", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["TOAN"], buckets: [{ codes: ["G11"], count: 17 }, { codes: ["G12"], count: 50 }] },
      { id: "q2", name: "Nhieu mon", dimension: "DIFFICULTY", basis: "QUESTION", enforcement: "REFERENCE", subjectCodes: ["TOAN", "LY"], buckets: [{ codes: ["NB"], count: 10 }] },
    ];
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    expect(within(reference).getByText(/67 lệnh/)).toBeInTheDocument();
    for (const id of ["q1", "q2"]) {
      const quota = reference.querySelector<HTMLElement>(`[data-reference-quota="${id}"]`)!;
      expect(within(quota).getByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeDisabled();
      expect(quota.querySelector("[data-lock-reason]")).not.toBeNull();
    }
    await user.click(within(rowOf("r1")).getByRole("button", { name: /Sửa dòng/ }));
    const name = screen.getByRole("textbox", { name: /Tên nhóm/ });
    await user.clear(name);
    await user.type(name, "Ten moi");
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(state.examQuotas[0]).toMatchObject({ basis: "PROMPT", enforcement: "REFERENCE" });
  });
  it("referenceWithApply: phân bố độ khó theo câu bật thành REQUIRED", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 25 })]));
    const { server } = boot({ kind: "VSAT", parts: rows });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    const difficulty = reference.querySelector<HTMLElement>('[data-reference-dimension="DIFFICULTY"]')!;
    await user.type(within(difficulty).getAllByRole("textbox", { name: /số câu/ })[0]!, "25");
    await user.click(within(difficulty).getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix).toMatchObject({ basis: "QUESTION", difficultyEnforcement: "REQUIRED" });
  });
  it("undeclaredApplyShowsChip: Phân bổ đối chiếu trống không hiện công tắc chết; nhập một ô mới hiện công tắc bật được REQUIRED", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 25 })]));
    const { server } = boot({ kind: "SP2E", parts: rows });
    const first = openStructure("?section=sec-toan");
    await first.findByRole("heading", { name: "Các dòng lấy câu" });
    const ref = await openReference(user);
    expect(within(ref).queryByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeNull();
    const difficulty = ref.querySelector<HTMLElement>('[data-reference-dimension="DIFFICULTY"]')!;
    expect(within(difficulty).getByText("Chưa khai báo")).toBeInTheDocument();
    await user.type(within(difficulty).getAllByRole("textbox", { name: /số câu/ })[0]!, "10");
    await user.click(within(difficulty).getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix).toMatchObject({ difficultyEnforcement: "REQUIRED" });
    first.unmount();

    const { server: server2 } = boot({ kind: "VSAT", parts: rows, examOverrides: { scope: "PART" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const vsatRef = await openReference(user);
    await user.click(within(vsatRef).getByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" }));
    await user.click(screen.getByRole("menuitem", { name: /^Kết hợp Lớp × Độ khó/ }));
    await user.click(within(vsatRef).getByRole("button", { name: "Xác nhận chuyển" }));
    const combined = vsatRef.querySelector<HTMLElement>('[data-reference-dimension="COMBINED"]')!;
    expect(within(combined).queryByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeNull();
    expect(within(combined).getByText("Chưa khai báo")).toBeInTheDocument();
    await user.type(within(combined).getAllByRole("textbox")[0]!, "10");
    await user.click(within(combined).getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server2)).toHaveLength(1));
    expect(sectionsOf(putCalls(server2)[0]!.body)["sec-toan"]!.matrix).toMatchObject({ mode: "COMBINED", gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" });
  });
  it("clearingRequiredDimensionOrCombinedResetsToReference: xóa hết ô sau khi bật Áp dụng khi sinh đề ở % đặt lại REFERENCE và lưu được (R2-1)", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 25 })]));
    const { server } = boot({ kind: "SP2E", parts: rows });
    const first = openStructure("?section=sec-toan");
    await first.findByRole("heading", { name: "Các dòng lấy câu" });
    const ref = await openReference(user);
    await user.click(within(within(ref).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" }));
    const grade = ref.querySelector<HTMLElement>('[data-reference-dimension="GRADE"]')!;
    const gradeCell = within(grade).getAllByRole("textbox", { name: /Khối lớp: .*tỷ lệ/ })[0]!;
    await user.type(gradeCell, "100");
    const difficulty = ref.querySelector<HTMLElement>('[data-reference-dimension="DIFFICULTY"]')!;
    const difficultyCell = within(difficulty).getAllByRole("textbox", { name: /Độ khó: .*tỷ lệ/ })[0]!;
    await user.type(difficultyCell, "100");
    await user.click(within(difficulty).getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.clear(difficultyCell);
    expect(within(difficulty).getByText("Chưa khai báo")).toBeInTheDocument();
    expect(within(difficulty).queryByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeNull();
    expect(within(difficulty).queryByRole("alert")).toBeNull();
    expect(within(ref).queryByText(/Tổng tỷ lệ 0%/)).toBeNull();
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix).toMatchObject({
      unit: "PERCENT",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
      byDifficulty: [],
    });
    first.unmount();

    const { server: server2 } = boot({ kind: "VSAT", parts: rows, examOverrides: { scope: "PART" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const vsatRef = await openReference(user);
    await user.click(within(vsatRef).getByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" }));
    await user.click(screen.getByRole("menuitem", { name: /^Kết hợp Lớp × Độ khó/ }));
    await user.click(within(vsatRef).getByRole("button", { name: "Xác nhận chuyển" }));
    await user.click(within(within(vsatRef).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" }));
    const combined = vsatRef.querySelector<HTMLElement>('[data-reference-dimension="COMBINED"]')!;
    const combinedCell = within(combined).getAllByRole("textbox")[0]!;
    await user.type(combinedCell, "100");
    await user.click(within(combined).getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    await user.clear(combinedCell);
    expect(within(combined).getByText("Chưa khai báo")).toBeInTheDocument();
    expect(within(combined).queryByRole("switch", { name: "Áp dụng khi sinh đề" })).toBeNull();
    expect(within(combined).queryByRole("alert")).toBeNull();
    expect(within(vsatRef).queryByText(/Tổng tỷ lệ 0%/)).toBeNull();
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server2)).toHaveLength(1));
    expect(sectionsOf(putCalls(server2)[0]!.body)["sec-toan"]!.matrix).toMatchObject({
      mode: "COMBINED",
      unit: "PERCENT",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
    });
  });
  it("referencePercentGuard: tổng 90% chặn PUT, đủ 100% lưu PERCENT và tải lại", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 40 })]));
    const { server } = boot({ kind: "SP2E", parts: rows });
    const view = openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    await user.click(within(within(reference).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" }));
    const cells = within(reference).getAllByRole("textbox", { name: /Độ khó: .*tỷ lệ/ });
    await user.type(cells[0]!, "90");
    expect(within(reference).getByText("Tổng tỷ lệ 90%, cần 100%")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    expect(putCalls(server)).toHaveLength(0);
    await user.clear(cells[0]!);
    await user.type(cells[0]!, "100");
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix as Wire;
    expect(saved.unit).toBe("PERCENT");
    expect(saved.byDifficulty).toEqual([{ code: "RECOGNITION", value: 100 }]);
    view.unmount();
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reopened = await openReference(user);
    expect(within(within(reopened).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" })).toBeChecked();
  });

  it("combinedOnlyVsatPartLevel: xem trước, hủy không đổi; xác nhận lưu bảng COMBINED", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 40 })]));
    const { server } = boot({ kind: "VSAT", parts: rows, examOverrides: { scope: "PART" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    const openCombined = async () => {
      await user.click(within(reference).getByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" }));
      await user.click(screen.getByRole("menuitem", { name: /^Kết hợp Lớp × Độ khó/ }));
    };
    await openCombined();
    expect(within(reference).getByRole("heading", { name: "Xem trước: sang bảng kết hợp" })).toBeInTheDocument();
    await user.click(within(reference).getByRole("button", { name: /^Hủy$/ }));
    expect(reference.querySelector("[data-reference-combined]")).toBeNull();
    expect(putCalls(server)).toHaveLength(0);
    await openCombined();
    await user.click(within(reference).getByRole("button", { name: "Xác nhận chuyển" }));
    expect(reference.querySelector("[data-reference-combined]")).not.toBeNull();
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix).toMatchObject({ mode: "COMBINED" });
  });

  it("combinedEditAndRevert: sửa ô kết hợp rồi bấm Bỏ kết hợp về SEPARATE với tổng biên; bảng trống báo đúng câu rỗng", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 40 })]));
    const { server } = boot({ kind: "VSAT", parts: rows, examOverrides: { scope: "PART" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    await user.click(within(reference).getByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" }));
    await user.click(screen.getByRole("menuitem", { name: /^Kết hợp Lớp × Độ khó/ }));
    await user.click(within(reference).getByRole("button", { name: "Xác nhận chuyển" }));

    // Bảng trống → Bỏ kết hợp hiện previewBackEmpty.
    await user.click(within(reference).getByRole("button", { name: "Bỏ kết hợp" }));
    expect(within(reference).getByText(/Bảng kết hợp chưa có số nào/)).toBeInTheDocument();
    await user.click(within(reference).getByRole("button", { name: /^Hủy$/ }));

    // Nhập số vào bảng kết hợp, rồi Bỏ kết hợp và Xác nhận chuyển.
    const combinedSection = reference.querySelector<HTMLElement>('[data-reference-combined]')!;
    const cells = within(combinedSection).getAllByRole("textbox");
    await user.clear(cells[0]!);
    await user.type(cells[0]!, "15");
    await user.click(within(combinedSection).getByRole("button", { name: "Bỏ kết hợp" }));
    expect(within(reference).getByRole("heading", { name: "Xem trước: về riêng từng chiều" })).toBeInTheDocument();
    await user.click(within(reference).getByRole("button", { name: "Xác nhận chuyển" }));
    expect(reference.querySelector("[data-reference-combined]")).toBeNull();
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.matrix).toMatchObject({ mode: "SEPARATE", byGrade: expect.arrayContaining([expect.objectContaining({ value: 15 })]) });
  });

  it("hiddenGradePercentSwitch: môn ẩn lớp có dữ liệu lớp tham khảo một phần đổi sang Tỷ lệ vẫn lưu được (R1-6)", async () => {
    const user = userEvent.setup();
    const section = wireSection("sec-su", "SU", [wireBlock("r1", { questionCount: 20 })], {
      matrix: matrixWith({ gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE", byGrade: [{ code: "GRADE_10", value: 5 }], byDifficulty: [{ code: "RECOGNITION", value: 20 }] }),
    });
    const { server } = boot({ kind: "SP2E", parts: oneSection(section) });
    openStructure("?section=sec-su");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    await user.click(within(within(reference).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" }));
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(sectionsOf(putCalls(server)[0]!.body)["sec-su"]!.matrix).toMatchObject({ unit: "PERCENT", byGrade: [{ code: "GRADE_10", value: 100 }] });
  });

  it("clearAllReferenceCellsReturnsNull: xóa hết ô trả section.matrix về null và giữ đơn vị Tỷ lệ đã chọn (R1-9)", async () => {
    const user = userEvent.setup();
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { questionCount: 40 })]));
    boot({ kind: "SP2E", parts: rows });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    const reference = await openReference(user);
    await user.click(within(within(reference).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" }));
    const firstCell = within(reference).getAllByRole("textbox", { name: /Độ khó: .*tỷ lệ/ })[0]!;
    await user.type(firstCell, "100");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.clear(firstCell);
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    expect(within(within(reference).getByRole("group", { name: "Đơn vị nhập" })).getByRole("radio", { name: "Tỷ lệ" })).toBeChecked();
  });

  it.each([["HSA", "VAN"], ["SP2E", "TOAN"], ["THPTQG", "TOAN"]])(
    "combinedOnlyVsatPartLevel: %s %s không có bảng kết hợp",
    async (kind, subject) => {
      const user = userEvent.setup();
      const rows = oneSection(wireSection("sec-x", subject, [wireBlock("r1", { questionCount: 20 })]));
      boot({ kind, parts: rows });
      openStructure("?section=sec-x");
      await screen.findByRole("heading", { name: "Các dòng lấy câu" });
      const reference = await openReference(user);
      expect(within(reference).queryByRole("button", { name: "Thêm điều kiện của Phân bổ đối chiếu" })).toBeNull();
      expect(reference.querySelector("[data-reference-combined]")).toBeNull();
    },
  );
});

describe("Danh sách dòng: số request, trường đã dọn (R-13)", () => {
  it("noExtraRequestsOnOpen: mở DS thật không gọi nhiều request hơn MD của cùng môn, cùng fixture", async () => {
    const open = async (useRows: boolean) => {
      const { server } = boot({
        kind: "QDA",
        parts: oneSection(useRows ? qdaLiteratureSection() : qdaLiteratureMdSection()),
      });
      const rendered = openStructure("?section=sec-van");
      await rendered.findByRole("heading", { name: useRows ? "Các dòng lấy câu" : "Nhóm lấy câu" });
      await waitFor(() => expect(server.requests.length).toBeGreaterThan(0));
      const log = requestLog(server);
      rendered.unmount();
      return log;
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
    const slotsRow = document.querySelector<HTMLElement>('[data-form-slots="van-5"] td[colspan]')!;
    expect(slotsRow).toHaveAttribute("colspan", "4");
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
    boot({ kind: "QDA", parts: oneSection(qdaLiteratureMdSection()) });
    openStructure("?section=sec-van");
    expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Khai chỉ tiêu" }).length).toBeGreaterThan(0);
  });
});
