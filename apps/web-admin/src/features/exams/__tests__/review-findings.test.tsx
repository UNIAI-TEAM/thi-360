import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { buildUnitRows, groupIssues, issueStructureHref } from "../check";
import { ExamReviewCheck } from "../components/exam-review-check";
import type { CheckIssue } from "../review-types";
import type { ExamDetail } from "../types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

const STRUCTURE = `/exams/${EXAM_ID}/structure`;

function issue(overrides: Record<string, unknown>) {
  return { severity: "ERROR", group: "STRUCTURE", affectedCodes: [], ...overrides };
}

/** Hai lỗi có chỗ sửa, một lỗi cả bài, hai cảnh báo (một theo mã), kèm trạng thái từng môn. */
function wire(issues: Record<string, unknown>[], sectionStates: Record<string, unknown>[] | null = null) {
  const base = checkResultWire("INVALID");
  return {
    ...base,
    issueCount: issues.length,
    sectionStates,
    groups: [{ group: "STRUCTURE", status: "FAIL", issues }],
    paths: [{ pathId: "default", result: "INVALID", issues: [] }],
    listingCheck: { group: "LISTING", status: "PASS", issues: [] },
  };
}

const ERRORS = [
  issue({
    key: "e1",
    code: "SECTION_INCOMPLETE",
    subjectCode: "MATH",
    partId: "part-1",
    sectionId: "sec-1",
    affectedCodes: ["QD01", "QD02"],
  }),
  issue({
    key: "e2",
    code: "ITEM_SHAPE_SHORTAGE",
    group: "SOURCE",
    subjectCode: "MATH",
    partId: "part-1",
    sectionId: "sec-1",
    blockId: "blk-1",
    blockName: "Choice",
    required: 5,
    available: 3,
  }),
  issue({ key: "e3", code: "PATH_TOTAL_MISMATCH", required: 150, available: 140 }),
];
const WARNINGS = [
  issue({
    key: "w1",
    code: "COMBINATION_LIST_CHANGED",
    severity: "WARNING",
    partId: "part-1",
    affectedCodes: ["QD03"],
  }),
  issue({ key: "w2", code: "MANUAL_GRADING_REQUIRED", severity: "WARNING", subjectCode: "MATH", blockId: "blk-1", blockName: "Choice" }),
];

function renderCheck(server: FakeServer, locale?: "en") {
  server.install();
  return renderWithProviders(
    <ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />,
    locale ? { locale, messages: en } : undefined,
  );
}

async function errorsList() {
  return within(await screen.findByTestId("findings-errors"));
}

describe("Kiểm tra: lỗi và cảnh báo tách riêng", () => {
  it("lỗi chặn công bố và cảnh báo nằm ở hai danh sách, kèm số đếm và câu kết luận", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire([...ERRORS, ...WARNINGS]) }));

    expect(await screen.findByText("Chưa công bố được: 3 lỗi chặn công bố.")).toBeInTheDocument();
    expect(screen.getByText(/2 cảnh báo không chặn công bố\./)).toBeInTheDocument();
    const errors = await errorsList();
    const warnings = within(screen.getByTestId("findings-warnings"));
    expect(errors.getByText("Lỗi · chặn công bố (3)")).toBeInTheDocument();
    expect(warnings.getByText("Cảnh báo · không chặn công bố (2)")).toBeInTheDocument();
    // Cảnh báo không lẫn vào danh sách lỗi, lỗi không lẫn vào danh sách cảnh báo.
    expect(errors.queryByText("COMBINATION_LIST_CHANGED")).not.toBeInTheDocument();
    expect(warnings.queryByText("SECTION_INCOMPLETE")).not.toBeInTheDocument();
  });

  it("lời lỗi là chữ chính, mã kỹ thuật là chữ phụ", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));

    const errors = await errorsList();
    const message = await errors.findByText("Môn MATH chưa hoàn tất cấu hình.");
    const code = errors.getByText("SECTION_INCOMPLETE");
    expect(code.className).toContain("text-caption");
    expect(message.className).toContain("text-body");
    expect(message.compareDocumentPosition(code) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("không có lỗi chặn thì báo công bố được; cảnh báo vẫn hiện", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(WARNINGS) }));

    expect(await screen.findByText("Công bố được. Không có lỗi chặn.")).toBeInTheDocument();
    expect(screen.queryByTestId("findings-errors")).not.toBeInTheDocument();
    expect(screen.getByTestId("findings-warnings")).toBeInTheDocument();
  });

  it("máy chủ không trả listingCheck: không báo công bố được, dùng dòng chưa kết luận", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: { ...wire(WARNINGS), listingCheck: null } }));

    expect(await screen.findByText("Chưa kết luận được: chưa kiểm tra được Thông tin bán.")).toBeInTheDocument();
    expect(screen.queryByText("Công bố được. Không có lỗi chặn.")).not.toBeInTheDocument();
  });

  it("lỗi mức UNCONFIRMED ở danh sách riêng, không gộp vào lỗi chặn", async () => {
    const unconfirmed = issue({ key: "u1", code: "OVERLAP_UNCONFIRMED", severity: "UNCONFIRMED", group: "OVERLAP", subjectCode: "MATH" });
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire([unconfirmed]) }));

    expect(await screen.findByText(/Chưa kết luận được: 1 vấn đề chưa xác nhận/)).toBeInTheDocument();
    expect(screen.getByTestId("findings-unconfirmed")).toBeInTheDocument();
    expect(screen.queryByTestId("findings-errors")).not.toBeInTheDocument();
  });
});

describe("Kiểm tra: gom theo môn và theo mã", () => {
  it("mặc định gom theo môn: lỗi không gắn môn nằm ở nhóm Cả bài", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));

    const errors = await errorsList();
    expect(errors.getByText("MATH")).toBeInTheDocument();
    expect(errors.getByText("Cả bài")).toBeInTheDocument();
  });

  it("gom theo mã: một lỗi của nhiều mã nằm ở từng mã; lỗi không gắn mã ở nhóm Không gắn mã", async () => {
    const user = userEvent.setup();
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));
    await errorsList();

    await user.click(screen.getByRole("tab", { name: "Mã" }));

    const errors = within(screen.getByTestId("findings-errors"));
    expect(errors.getByText("Mã QD01")).toBeInTheDocument();
    expect(errors.getByText("Mã QD02")).toBeInTheDocument();
    expect(errors.getByText("Không gắn mã")).toBeInTheDocument();
    expect(errors.getAllByText("Môn MATH chưa hoàn tất cấu hình.")).toHaveLength(2);
  });

  it("nêu các mã bài thi bị ảnh hưởng của lỗi", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));

    const errors = await errorsList();
    const affected = errors.getByText("Mã bị ảnh hưởng:").parentElement!;
    expect(within(affected).getByText("QD01")).toBeInTheDocument();
    expect(within(affected).getByText("QD02")).toBeInTheDocument();
  });
});

describe("Kiểm tra: bấm lỗi mở đúng chỗ ở bước Cấu trúc", () => {
  it("khối, môn, lỗi cả bài: đường dẫn theo chỗ nhỏ nhất biết được, lỗi cả bài không có liên kết", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));

    const errors = await errorsList();
    const links = errors.getAllByRole("link");
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      `${STRUCTURE}?section=sec-1`,
      `${STRUCTURE}?block=blk-1`,
    ]);
    expect(links[1]).toHaveAccessibleName(/^Mở chỗ lỗi: /);
  });

  it("issueStructureHref: lựa chọn của phần, phần, và không có chỗ nào", () => {
    const base = { blockId: null, sectionId: null, partId: null, optionId: null } as unknown as CheckIssue;
    expect(issueStructureHref("x", { ...base, partId: "p1", optionId: "o1" })).toBe("/exams/x/structure?part=p1&option=o1");
    expect(issueStructureHref("x", { ...base, partId: "p1" })).toBe("/exams/x/structure?part=p1");
    expect(issueStructureHref("x", base)).toBeNull();
  });
});

describe("Kiểm tra: trạng thái từng môn và bảng theo mã, theo phần", () => {
  const states = [{ partId: "part-1", sectionId: "sec-1", subjectCode: "MATH", state: "INCOMPLETE" }];

  it("chip trạng thái từng môn và số lỗi, cảnh báo theo phần", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire([...ERRORS, ...WARNINGS], states) }));

    expect(await screen.findByText("Môn đã kiểm tra")).toBeInTheDocument();
    expect(screen.getByText("0/1")).toBeInTheDocument();
    const table = await screen.findByRole("table", { name: "Theo mã và phần" });
    const row = within(table).getByText("Part 1").closest("tr")!;
    expect(within(row).getByText("Chưa hoàn tất")).toBeInTheDocument();
    // Phần part-1: 2 lỗi (e1, e2) và 2 cảnh báo (w1 gắn phần; w2 chỉ có blockId blk-1 của phần này).
    const cells = within(row).getAllByRole("cell");
    expect(cells[3]).toHaveTextContent("2");
    expect(cells[4]).toHaveTextContent("2");
  });

  it("số 0 trong bảng theo phần là dấu '–' trung tính; chỉ số lớn hơn 0 mới tô màu lỗi, cảnh báo", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS, states) }));

    const table = await screen.findByRole("table", { name: "Theo mã và phần" });
    const cells = within(within(table).getByText("Part 1").closest("tr")!).getAllByRole("cell");
    expect(within(cells[3]!).getByText("2")).toHaveClass("text-danger");
    const dash = within(cells[4]!).getByText("–");
    expect(dash).not.toHaveClass("text-warning");
    expect(dash).not.toHaveClass("text-danger");
  });

  it("không có sectionStates (null) thì không hiện trạng thái môn", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }));

    await errorsList();
    expect(screen.queryByText("Môn đã kiểm tra")).not.toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Theo mã và phần" })).not.toBeInTheDocument();
  });

  it("buildUnitRows: dòng mã của phần theo mã lấy trạng thái xấu nhất của các môn của mã và số lỗi theo affectedCodes", () => {
    const section = (id: string, subjectCode: string) => ({ id, subjectCode, blocks: [] });
    const parts = [
      {
        id: "p3",
        order: 3,
        name: "Part 3",
        sections: [],
        options: [],
        subjectPool: [section("s-phy", "PHY"), section("s-chem", "CHEM"), section("s-bio", "BIO")],
      },
    ] as unknown as ExamDetail["parts"];
    const combinations = [
      { code: "QD01", examCode: "QD01", partId: "p3", subjectCodes: ["PHY", "CHEM"], missingSubjects: [], nameVi: null, nameEn: null },
      { code: "QD03", examCode: "QD03", partId: "p3", subjectCodes: ["BIO", "PHY"], missingSubjects: [], nameVi: null, nameEn: null },
    ];
    const sectionStates = [
      { partId: "p3", sectionId: "s-phy", subjectCode: "PHY", state: "CHECKED" as const },
      { partId: "p3", sectionId: "s-chem", subjectCode: "CHEM", state: "INCOMPLETE" as const },
      { partId: "p3", sectionId: "s-bio", subjectCode: "BIO", state: "NEEDS_CHECK" as const },
    ];
    const issues = [
      { key: "a", severity: "ERROR", affectedCodes: ["QD01"], partId: "p3" },
      { key: "b", severity: "WARNING", affectedCodes: ["QD03"], partId: "p3" },
    ] as unknown as CheckIssue[];

    const rows = buildUnitRows({ parts }, combinations, sectionStates, issues);

    expect(rows.map((row) => [row.name, row.state, row.errors, row.warnings])).toEqual([
      ["Part 3", "INCOMPLETE", 1, 1],
      ["QD01", "INCOMPLETE", 1, 0],
      ["QD03", "NEEDS_CHECK", 0, 1],
    ]);
  });

  it("buildUnitRows: nhãn dòng mã là mã tổ hợp (QD01..QD04) dù cấu trúc đặt examCode là mã loại bài (QDA); số lỗi vẫn theo affectedCodes", () => {
    const section = (id: string, subjectCode: string) => ({ id, subjectCode, blocks: [] });
    const parts = [
      { id: "p3", order: 3, name: "Part 3", sections: [], options: [], subjectPool: [section("s-phy", "PHY")] },
    ] as unknown as ExamDetail["parts"];
    const combinations = ["QD01", "QD02", "QD03", "QD04"].map((code) => ({
      code,
      examCode: "QDA",
      partId: "p3",
      subjectCodes: ["PHY"],
      missingSubjects: [],
      nameVi: null,
      nameEn: null,
    }));
    const issues = [{ key: "a", severity: "ERROR", affectedCodes: ["QD01", "QD03"], partId: "p3" }] as unknown as CheckIssue[];

    const rows = buildUnitRows({ parts }, combinations, [], issues);

    expect(rows.filter((row) => row.kind === "code").map((row) => [row.name, row.errors])).toEqual([
      ["QD01", 1],
      ["QD02", 0],
      ["QD03", 1],
      ["QD04", 0],
    ]);
  });

  it("buildUnitRows: lỗi chỉ có blockId (bộ giải) hoặc sectionId vẫn tính vào dòng phần chứa khối, môn đó", () => {
    const block = (id: string) => ({ id, name: id });
    const parts = [
      { id: "p1", order: 1, name: "Part 1", options: [], sections: [{ id: "s1", subjectCode: "MATH", blocks: [block("b1")] }] },
      {
        id: "p2",
        order: 2,
        name: "Part 2",
        sections: [],
        subjectPool: [],
        options: [{ id: "o1", sections: [{ id: "s2", subjectCode: "PHY", blocks: [block("b2")] }], blocks: [] }],
      },
    ] as unknown as ExamDetail["parts"];
    const issues = [
      { key: "a", severity: "ERROR", partId: null, sectionId: null, blockId: "b1" },
      { key: "b", severity: "WARNING", partId: null, sectionId: null, blockId: "b2" },
      { key: "c", severity: "ERROR", partId: null, sectionId: "s2", blockId: null },
      { key: "d", severity: "ERROR", partId: null, sectionId: null, blockId: null },
    ] as unknown as CheckIssue[];

    const rows = buildUnitRows({ parts }, [], [], issues);

    // Lỗi cả bài (d) không thuộc phần nào; b1 → Part 1; b2, s2 → Part 2.
    expect(rows.map((row) => [row.name, row.errors, row.warnings])).toEqual([
      ["Part 1", 1, 0],
      ["Part 2", 1, 1],
    ]);
  });

  it("groupIssues: nhóm không gắn đứng cuối", () => {
    const list = [
      { key: "1", subjectCode: null },
      { key: "2", subjectCode: "MATH" },
    ] as unknown as CheckIssue[];

    expect(groupIssues(list, "subject").map((group) => group.value)).toEqual(["MATH", null]);
  });
});

describe("Kiểm tra: tiếng Anh", () => {
  it("câu kết luận và nhãn nhóm theo ngôn ngữ en", async () => {
    renderCheck(new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire(ERRORS) }), "en");

    expect(await screen.findByText("Cannot publish yet: 3 errors block publishing.")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Code" })).toBeInTheDocument();
  });
});
