import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { EXAMS_QUERY_KEYS } from "../keys";
import { commandDenominators, complianceLevel, isUnclassifiedRow } from "../review-compliance";
import type { PreviewCompliance, PreviewPaper, PreviewSubject } from "../review-types";
import type { ExamScope } from "../types";
import { EXAM_ID, examDetailWire } from "./fake-exams-server";
import { paperOf, partOf } from "./preview-paper-fixtures";
import { byTextContent } from "./text-content";

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
  originalRequired: null,
  topicName: null,
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

/** Chỉ tiêu cấp bài của đề V-SAT Toán thật (visual r3): khối lớp rồi độ khó, cùng tổng 67 ở scope cả bài. */
const VSAT_EXAM_ROWS: PreviewCompliance[] = [
  row("EXAM:vsat-toan-grade:G10", 0, 0, { scope: "EXAM" }),
  row("EXAM:vsat-toan-grade:G11", 17, 16, { scope: "EXAM" }),
  row("EXAM:vsat-toan-grade:G12", 50, 47, { scope: "EXAM" }),
  row("EXAM:vsat-toan-difficulty:NB", 13, 13, { scope: "EXAM" }),
  row("EXAM:vsat-toan-difficulty:TH", 35, 33, { scope: "EXAM" }),
  row("EXAM:vsat-toan-difficulty:VD", 19, 17, { scope: "EXAM" }),
  row("GRADE:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
];

const subject = (sectionId: string, subjectCode: string): PreviewSubject => ({ sectionId, subjectCode, questionCount: 25, quotas: [] });

const paper: PreviewPaper = {
  ...paperOf([partOf({ partId: "p-ii", name: "Phan II", blocks: [] })]),
  subjects: [{ sectionId: "sec-math", subjectCode: "MATH", questionCount: 25, quotas: [] }],
};

/** Đề V-SAT Toán thật: 4 mục môn cùng `subjectCode` nên `paper.subjects.length` không còn là 1. */
const vsatPaper: PreviewPaper = {
  ...paper,
  subjects: [subject("sec-1", "MATH"), subject("sec-2", "MATH"), subject("sec-3", "MATH"), subject("sec-4", "MATH")],
};

function renderTable(rows: PreviewCompliance[], scope: ExamScope = "SUBJECT", options: { locale?: "en"; paper?: PreviewPaper; subjectCode?: string } = {}) {
  const exam = mapExamDetail(examDetailWire({ scope, ...(options.subjectCode !== undefined ? { subjectCode: options.subjectCode } : {}) }));
  const view = renderWithProviders(
    <ExamPreviewCompliance paper={options.paper ?? paper} compliance={rows} dropped={[]} examId={EXAM_ID} />,
    options.locale ? { locale: options.locale, messages: en } : undefined,
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
    renderTable(COMMAND_ROWS, "SUBJECT", { locale: "en" });

    expect(await screen.findByText("Denominator: Mathematics, whole subject · 67 commands")).toBeInTheDocument();
  });

  it("bài theo phần: mẫu số nêu phần thay cho 'cả môn'", async () => {
    renderTable([row("DIFFICULTY:RECOGNITION", 2, 2), row("DIFFICULTY:COMPREHENSION", 4, 4)], "PART");

    const denominator = await screen.findByText(byTextContent("Mẫu số: Toán · Phan II · 6 lệnh"));
    expect(denominator).toBeInTheDocument();
    expect(denominator.querySelector("[data-exam-content]")).toHaveTextContent("Phan II");
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
    // Hai phạm vi mỗi phạm vi một dòng mẫu số: môn (SECTION) và cả bài (EXAM); dòng Chưa phân loại không sinh mẫu số.
    expect(await screen.findAllByText(/^Mẫu số:/)).toHaveLength(2);
  });

  it("đề V-SAT cả môn (chỉ tiêu cấp bài): mẫu số 'Toán cả môn · 67 lệnh' đúng một lần, không phải 134", async () => {
    // Hai chỉ tiêu cấp bài (khối lớp 67, độ khó 67) vẫn chỉ sinh một dòng mẫu số.
    renderTable(VSAT_EXAM_ROWS);

    expect(await screen.findAllByText("Mẫu số: Toán cả môn · 67 lệnh")).toHaveLength(1);
    expect(document.body.textContent).not.toMatch(/134 lệnh/);
  });

  it("en: đề V-SAT cả môn hiện 'Mathematics, whole subject · 67 commands' một lần", async () => {
    const examRows: PreviewCompliance[] = [
      row("EXAM:vsat-toan-grade:G10,G11,G12", 67, 63, { scope: "EXAM" }),
      row("EXAM:vsat-toan-difficulty:NB,TH,VD", 67, 63, { scope: "EXAM" }),
    ];
    renderTable(examRows, "SUBJECT", { locale: "en" });

    expect(await screen.findAllByText("Denominator: Mathematics, whole subject · 67 commands")).toHaveLength(1);
  });

  it("đề V-SAT nhiều mục cùng môn: mẫu số cả bài lấy môn chung của các mục, không bỏ trống tên", async () => {
    // Hình dạng thật của VSAT_SUBJECT_TOAN: 4 phần đều là môn Toán nên `paper.subjects` có 4 mục một `subjectCode`.
    renderTable(VSAT_EXAM_ROWS, "SUBJECT", { paper: vsatPaper });

    expect(await screen.findAllByText("Mẫu số: Toán cả môn · 67 lệnh")).toHaveLength(1);
  });

  it("en: đề V-SAT nhiều mục cùng môn hiện 'Mathematics, whole subject · 67 commands'", async () => {
    renderTable(VSAT_EXAM_ROWS, "SUBJECT", { paper: vsatPaper, locale: "en" });

    expect(await screen.findAllByText("Denominator: Mathematics, whole subject · 67 commands")).toHaveLength(1);
  });

  it("đề V-SAT nhiều mục cùng môn: ưu tiên `subjectCode` của bài khi có", async () => {
    // Khi bài mang sẵn `subjectCode` thì dùng nó ngay cả khi đề có nhiều mục khác môn (dữ liệu lạ).
    const mixedPaper: PreviewPaper = { ...paper, subjects: [subject("sec-1", "MATH"), subject("sec-2", "LITERATURE")] };
    renderTable(VSAT_EXAM_ROWS, "SUBJECT", { paper: mixedPaper, subjectCode: "MATH" });

    expect(await screen.findAllByText("Mẫu số: Toán cả môn · 67 lệnh")).toHaveLength(1);
  });

  it("nhiều môn khác nhau và bài không `subjectCode`: mẫu số không tên môn, không ô trống", async () => {
    const mixedPaper: PreviewPaper = { ...paper, subjects: [subject("sec-1", "MATH"), subject("sec-2", "LITERATURE")] };
    renderTable(VSAT_EXAM_ROWS, "SUBJECT", { paper: mixedPaper });

    expect(await screen.findAllByText("Mẫu số: cả môn · 67 lệnh")).toHaveLength(1);
  });

  it("en: nhiều môn khác nhau và bài không `subjectCode` hiện 'whole subject' không dấu phẩy đầu", async () => {
    const mixedPaper: PreviewPaper = { ...paper, subjects: [subject("sec-1", "MATH"), subject("sec-2", "LITERATURE")] };
    renderTable(VSAT_EXAM_ROWS, "SUBJECT", { paper: mixedPaper, locale: "en" });

    expect(await screen.findAllByText("Denominator: whole subject · 67 commands")).toHaveLength(1);
    expect(screen.queryByText(/^Denominator: ,/)).not.toBeInTheDocument();
  });

  it("mẫu số theo mục môn (SECTION) trên đề nhiều mục vẫn lấy đúng môn của mục", async () => {
    // Ma trận một môn/mục không đổi: dòng SECTION vẫn tra `sectionId` chứ không cần môn của bài.
    const mixedPaper: PreviewPaper = { ...paper, subjects: [subject("sec-math", "MATH"), subject("sec-lit", "LITERATURE")] };
    renderTable(COMMAND_ROWS, "SUBJECT", { paper: mixedPaper });

    expect(await screen.findAllByText("Mẫu số: Toán cả môn · 67 lệnh")).toHaveLength(1);
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

  it("scope cả bài (EXAM): chỉ tiêu cấp bài đầu tiên làm mẫu số, chỉ tiêu thứ hai và dòng Chưa phân loại không góp vào", () => {
    // Scope EXAM gửi chỉ tiêu tổng hợp `EXAM:<id>:<mã>` (khối lớp rồi độ khó, cùng tổng) và dòng UNCLASSIFIED.
    const examRows: PreviewCompliance[] = [
      row("EXAM:quota-grades:GRADE_11,GRADE_12", 67, 63, { scope: "EXAM" }),
      row("GRADE:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
      row("DIFFICULTY:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
    ];

    expect(commandDenominators(examRows)).toEqual([{ scope: "EXAM", total: 67 }]);
    expect(commandDenominators([...COMMAND_ROWS, ...examRows])).toEqual([
      { scope: "SECTION:sec-math", total: 67 },
      { scope: "EXAM", total: 67 },
    ]);
  });

  it("scope EXAM của đề V-SAT: chỉ tiêu khối lớp + độ khó cho một mẫu số 67 (không 134, không hai dòng)", () => {
    // Hình dạng dây thật của VSAT_SUBJECT_TOAN: chỉ tiêu khối lớp trước, độ khó sau, cùng tổng 67.
    const examRows: PreviewCompliance[] = [
      row("EXAM:vsat-toan-grade:G10", 0, 0, { scope: "EXAM" }),
      row("EXAM:vsat-toan-grade:G11", 17, 16, { scope: "EXAM" }),
      row("EXAM:vsat-toan-grade:G12", 50, 47, { scope: "EXAM" }),
      row("EXAM:vsat-toan-difficulty:NB", 13, 13, { scope: "EXAM" }),
      row("EXAM:vsat-toan-difficulty:TH", 35, 33, { scope: "EXAM" }),
      row("EXAM:vsat-toan-difficulty:VD", 19, 17, { scope: "EXAM" }),
      row("TOPIC:quota-1", 3, 3, { scope: "EXAM" }),
      row("GRADE:UNCLASSIFIED", null, 4, { scope: "EXAM", note: "UNCLASSIFIED" }),
    ];

    expect(commandDenominators(examRows)).toEqual([{ scope: "EXAM", total: 67 }]);
  });

  it("scope EXAM chỉ có chỉ tiêu độ khó (môn không lớp): mẫu số là tổng chỉ tiêu đó", () => {
    const examRows: PreviewCompliance[] = [
      row("EXAM:eng-difficulty:NB", 20, 20, { scope: "EXAM" }),
      row("EXAM:eng-difficulty:TH", 42, 42, { scope: "EXAM" }),
      row("EXAM:eng-difficulty:VD", 5, 5, { scope: "EXAM" }),
    ];

    expect(commandDenominators(examRows)).toEqual([{ scope: "EXAM", total: 67 }]);
  });

  it("scope EXAM: ô nhiều mã `EXAM:<id>:G10,G11` cộng một lần, không nhân theo số mã", () => {
    const examRows: PreviewCompliance[] = [
      row("EXAM:quota-grades:G10,G11", 17, 16, { scope: "EXAM" }),
      row("EXAM:quota-grades:G12", 50, 47, { scope: "EXAM" }),
      row("EXAM:quota-difficulty:NB,TH,VD", 67, 63, { scope: "EXAM" }),
    ];

    expect(commandDenominators(examRows)).toEqual([{ scope: "EXAM", total: 67 }]);
  });

  it("mã Chưa phân loại: chỉ mã UNCLASSIFIED của khối lớp hoặc độ khó", () => {
    expect(isUnclassifiedRow(row("GRADE:UNCLASSIFIED", null, 1))).toBe(true);
    expect(isUnclassifiedRow(row("DIFFICULTY:UNCLASSIFIED", null, 1))).toBe(true);
    expect(isUnclassifiedRow(row("DIFFICULTY:", 1, 1))).toBe(false);
    expect(isUnclassifiedRow(row("GRADE:GRADE_12", 1, 1))).toBe(false);
    expect(isUnclassifiedRow(row("TOPIC:UNCLASSIFIED", 1, 1))).toBe(false);
  });
});
