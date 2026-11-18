import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { EXAMS_QUERY_KEYS } from "../keys";
import { commandDenominators, complianceLevel, isUnclassifiedRow } from "../review-compliance";
import type { PreviewCompliance, PreviewPaper } from "../review-types";
import type { ExamScope } from "../types";
import { EXAM_ID, examDetailWire } from "./fake-exams-server";
import { paperOf, partOf } from "./preview-paper-fixtures";

// Nhà máy của `vi.mock` được nâng lên đầu tệp nên không dùng được import tĩnh cho dữ liệu mẫu (cùng cách các test khác).
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const row = (criterion: string, required: number | null, actual: number, over: Partial<PreviewCompliance> = {}): PreviewCompliance => ({
  criterion,
  scope: "SECTION:sec-math",
  required,
  actual,
  enforcement: "REFERENCE",
  basis: "PROMPT",
  independent: false,
  note: null,
  ...over,
});

/** Toán V-SAT cả môn (spec 5.3): 67 lệnh theo lớp (0 / 17 / 50) và theo độ khó (13 / 35 / 19), kèm dòng Chưa phân loại. */
const COMMAND_ROWS: PreviewCompliance[] = [
  row("GRADE:GRADE_10", 0, 0),
  row("GRADE:GRADE_11", 17, 16),
  row("GRADE:GRADE_12", 50, 47),
  row("GRADE:UNCLASSIFIED", null, 4, { note: "UNCLASSIFIED" }),
  row("DIFFICULTY:RECOGNITION", 13, 13),
  row("DIFFICULTY:COMPREHENSION", 35, 33),
  row("DIFFICULTY:APPLICATION", 19, 17),
  row("DIFFICULTY:UNCLASSIFIED", null, 4, { note: "UNCLASSIFIED" }),
];

const paper: PreviewPaper = {
  ...paperOf([partOf({ partId: "p-ii", name: "Phan II", blocks: [] })]),
  subjects: [{ sectionId: "sec-math", subjectCode: "MATH", questionCount: 25, quotas: [] }],
};

function renderTable(rows: PreviewCompliance[], scope: ExamScope = "SUBJECT", locale?: "en") {
  const exam = mapExamDetail(examDetailWire({ scope }));
  const view = renderWithProviders(
    <ExamPreviewCompliance paper={paper} compliance={rows} dropped={[]} examId={EXAM_ID} />,
    locale ? { locale, messages: en } : undefined,
  );
  view.client.setQueryData(EXAMS_QUERY_KEYS.detail(EXAM_ID), exam);
  return view;
}

describe("đối chiếu theo lệnh: mẫu số và dòng Chưa phân loại (R-139, D21)", () => {
  it("mẫu số đọc 'Toán cả môn · 67 lệnh', không có chữ FULL", async () => {
    renderTable(COMMAND_ROWS);

    expect(await screen.findByText("Mẫu số: Toán cả môn · 67 lệnh")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/FULL/);
  });

  it("en: 'Math, whole subject · 67 commands'", async () => {
    renderTable(COMMAND_ROWS, "SUBJECT", "en");

    expect(await screen.findByText("Denominator: Mathematics, whole subject · 67 commands")).toBeInTheDocument();
  });

  it("bài theo phần: mẫu số nêu phần thay cho 'cả môn'", async () => {
    renderTable([row("DIFFICULTY:RECOGNITION", 2, 2), row("DIFFICULTY:COMPREHENSION", 4, 4)], "PART");

    expect(await screen.findByText("Mẫu số: Toán · Phan II · 6 lệnh")).toBeInTheDocument();
  });

  it("có dòng Chưa phân loại ở cả khối lớp và độ khó, kết quả Thiếu nhãn, không hiện mã thô", async () => {
    renderTable(COMMAND_ROWS);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    // Tên mã chỉ có sau khi danh mục tải xong.
    await within(table).findByText("Khối lớp Lớp 11");
    const unclassified = within(table)
      .getAllByRole("row")
      .filter((tr) => within(tr).queryByText("Chưa phân loại"));
    expect(unclassified).toHaveLength(2);
    for (const tr of unclassified) {
      expect(within(tr).getByText("Thiếu nhãn")).toBeInTheDocument();
      expect(within(tr).getByText("—")).toBeInTheDocument();
      expect(within(tr).getByText("+4")).toBeInTheDocument();
    }
    expect(table.textContent).not.toMatch(/UNCLASSIFIED|GRADE_|DIFFICULTY|APPLICATION/);
  });

  it("dòng Chưa phân loại không tính là lệch; các dòng thật lệch vẫn đếm", async () => {
    renderTable(COMMAND_ROWS);

    expect(await screen.findByText("4 dòng lệch")).toBeInTheDocument();
    expect(complianceLevel(COMMAND_ROWS[3]!)).toBe("OK");
  });

  it("không có lệnh thiếu nhãn thì máy chủ không gửi dòng Chưa phân loại và bảng đạt", async () => {
    renderTable([row("GRADE:GRADE_12", 67, 67)]);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    expect(within(table).queryByText("Chưa phân loại")).not.toBeInTheDocument();
    expect(await screen.findByText("Đạt mọi tiêu chí")).toBeInTheDocument();
  });
  it("dòng cấp bài (scope Cả bài): chỉ tiêu EXAM:<id>:<mã> nêu tên mã, Chưa phân loại hiện Cả bài, không mã thô, không dòng mẫu số thứ hai", async () => {
    // Hình dạng dây thật của máy chủ ở scope cả bài: chỉ tiêu tổng hợp `EXAM:<id>:<mã>` và các dòng
    // `GRADE/DIFFICULTY:UNCLASSIFIED`; máy chủ không gửi `GRADE:<mã>`/`DIFFICULTY:<mã>` ở scope này.
    const examRows: PreviewCompliance[] = [
      ...COMMAND_ROWS,
      row("EXAM:quota-grades:GRADE_11,GRADE_12", 67, 63, { scope: "EXAM" }),
      row("GRADE:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
      row("DIFFICULTY:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
    ];
    renderTable(examRows);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    // Tên mã chỉ có sau khi danh mục tải xong.
    const quota = await within(table).findByText("Chỉ tiêu cấp bài Lớp 11 + Lớp 12");
    expect(within(quota.closest("tr")!).getByText("Cả bài")).toBeInTheDocument();
    const unclassified = within(table)
      .getAllByRole("row")
      .filter((tr) => within(tr).queryByText("Chưa phân loại"));
    expect(unclassified).toHaveLength(4);
    for (const tr of unclassified) {
      expect(within(tr).getByText("Thiếu nhãn")).toBeInTheDocument();
      expect(within(tr).getByText("—")).toBeInTheDocument();
      expect(within(tr).getByText("+4")).toBeInTheDocument();
    }
    const atExam = unclassified.filter((tr) => within(tr).queryByText("Cả bài"));
    expect(atExam).toHaveLength(2);
    expect(table.textContent).not.toMatch(/UNCLASSIFIED|GRADE_1[12]|quota-grades/);
    // Mẫu số chỉ có một, của môn; dòng cấp bài và Chưa phân loại cấp bài không sinh mẫu số riêng.
    expect(await screen.findAllByText(/^Mẫu số:/)).toHaveLength(1);
  });

  it("bài theo lệnh không đặt phạm vi môn hoặc phần (cả bài): không có dòng mẫu số", async () => {
    renderTable(COMMAND_ROWS, "FULL");

    await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    expect(screen.queryByText(/^Mẫu số:/)).not.toBeInTheDocument();
  });
});

describe("mẫu số theo lệnh", () => {
  it("cộng yêu cầu các dòng khối lớp; môn không có lớp (Địa lí) cộng theo độ khó; bỏ dòng Chưa phân loại và dòng không phải lệnh", () => {
    const geography = [row("DIFFICULTY:RECOGNITION", 20, 20), row("DIFFICULTY:COMPREHENSION", 42, 42), row("DIFFICULTY:APPLICATION", 5, 5)];

    expect(commandDenominators(COMMAND_ROWS)).toEqual([{ scope: "SECTION:sec-math", total: 67 }]);
    expect(commandDenominators(geography)).toEqual([{ scope: "SECTION:sec-math", total: 67 }]);
    expect(commandDenominators([row("GRADE:GRADE_12", 5, 5, { basis: "QUESTION" }), row("GRADE:GRADE_11", 5, 5, { enforcement: "REQUIRED" })])).toEqual([]);
  });

  it("scope cả bài (EXAM): chỉ tiêu EXAM:<id>:<mã> và dòng Chưa phân loại không góp vào mẫu số", () => {
    // Scope EXAM chỉ có chỉ tiêu tổng hợp và dòng UNCLASSIFIED, không có dòng GRADE/DIFFICULTY:<mã>.
    const examRows: PreviewCompliance[] = [
      row("EXAM:quota-grades:GRADE_11,GRADE_12", 67, 63, { scope: "EXAM" }),
      row("GRADE:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
      row("DIFFICULTY:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
    ];

    expect(commandDenominators(examRows)).toEqual([]);
    expect(commandDenominators([...COMMAND_ROWS, ...examRows])).toEqual([{ scope: "SECTION:sec-math", total: 67 }]);
  });

  it("mã Chưa phân loại: chỉ mã UNCLASSIFIED của khối lớp hoặc độ khó", () => {
    expect(isUnclassifiedRow(row("GRADE:UNCLASSIFIED", null, 1))).toBe(true);
    expect(isUnclassifiedRow(row("DIFFICULTY:UNCLASSIFIED", null, 1))).toBe(true);
    expect(isUnclassifiedRow(row("DIFFICULTY:", 1, 1))).toBe(false);
    expect(isUnclassifiedRow(row("GRADE:GRADE_12", 1, 1))).toBe(false);
    expect(isUnclassifiedRow(row("TOPIC:UNCLASSIFIED", 1, 1))).toBe(false);
  });
});
