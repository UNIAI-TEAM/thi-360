import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { combinationLabel } from "../check";
import { ExamPreviewPanel } from "../components/exam-preview-panel";
import { ExamReviewCheck } from "../components/exam-review-check";
import type { ExamCheckResult } from "../review-types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

/** Chữ cái tiếng Việt (kể cả Đ/đ): tiếng Anh không được chứa. */
const VIETNAMESE = /[À-ỹĐđ]/;

/** Hình dạng thật của `GET /exams/{id}/structure`: mã QD02, tên vi mở đầu bằng QĐ02 (Đ), tên en mở đầu bằng QD02. */
const COMBINATIONS = [
  { code: "QD01", examCode: "QDA", partId: "p3", subjectCodes: [], missingSubjects: [], nameVi: "QĐ01 · Vật lí – Hóa học", nameEn: "QD01 · Physics – Chemistry" },
  { code: "QD02", examCode: "QDA", partId: "p3", subjectCodes: [], missingSubjects: [], nameVi: "QĐ02 · Hóa học – Sinh học", nameEn: "QD02 · Chemistry – Biology" },
];
const STRUCTURE_WIRE = { examId: EXAM_ID, revision: 7, parts: [], combinations: COMBINATIONS };

/** Tên trong đường của kết quả kiểm tra / xem trước: tiếng Việt từ danh mục (trường hợp xấu nhất cho tiếng Anh). */
const choice = (code: string, name: string) => ({ partId: "p3", optionId: `p3:${code}`, kind: "COMBINATION", code, name });
const PATHS = [
  { pathId: "p3:QD02", choices: [choice("QD02", "QĐ02 · Hóa học – Sinh học")] },
  { pathId: "p3:QD01", choices: [choice("QD01", "QĐ01 · Vật lí – Hóa học")] },
];

const LABELS = {
  vi: { QD02: "QĐ02 · Hóa học – Sinh học", QD01: "QĐ01 · Vật lí – Hóa học" },
  en: { QD02: "QD02 · Chemistry – Biology", QD01: "QD01 · Physics – Chemistry" },
} as const;

const BCA_PARTS = [
  { id: "p1", order: 1, name: "Part 1", durationMinutes: 60, questionCount: 40, participation: { mode: "ALL" }, sections: [] },
  {
    id: "p3",
    order: 3,
    name: "Part 3",
    durationMinutes: 60,
    questionCount: 15,
    participation: { mode: "CHOICE", choose: 1 },
    codeSource: { source: "GLOBAL_LIST", branch: null },
    options: [],
    sections: [],
  },
];

function server() {
  return new FakeServer().on("GET", `${EXAM_PATH}/structure`, { status: 200, body: STRUCTURE_WIRE });
}

function countOf(text: string, part: string) {
  return text.split(part).length - 1;
}

describe("combinationLabel", () => {
  const combination = COMBINATIONS[1]!;

  it("tên theo ngôn ngữ giao diện; en-GB (giá trị next-intl trả cho tiếng Anh) là tiếng Anh", () => {
    expect(combinationLabel("QD02", { combination, locale: "vi" })).toBe("QĐ02 · Hóa học – Sinh học");
    expect(combinationLabel("QD02", { combination, locale: "en" })).toBe("QD02 · Chemistry – Biology");
    expect(combinationLabel("QD02", { combination, locale: "en-GB", fallback: "QĐ02 · Hóa học – Sinh học" })).toBe(
      "QD02 · Chemistry – Biology",
    );
  });

  it("không in lặp mã: tên đã mở đầu bằng mã (so sau khi đổi Đ thành D), tên trống hoặc tên = mã", () => {
    expect(combinationLabel("QD02", { combination: { ...combination, nameVi: "Hóa học – Sinh học" }, locale: "vi" })).toBe(
      "QD02 · Hóa học – Sinh học",
    );
    expect(combinationLabel("QD02", { combination: { ...combination, nameVi: "qđ02 · Hóa học" }, locale: "vi" })).toBe("qđ02 · Hóa học");
    expect(combinationLabel("QD01", { locale: "vi", fallback: "QD01" })).toBe("QD01");
    expect(combinationLabel("QD01", { locale: "vi", fallback: "Tổ hợp một" })).toBe("QD01 · Tổ hợp một");
  });

  it("không có tên theo ngôn ngữ thì dùng tên dự phòng, không mượn tên ngôn ngữ kia", () => {
    expect(combinationLabel("QD02", { combination: { ...combination, nameEn: null }, locale: "en-GB" })).toBe("QD02");
    expect(combinationLabel("QD02", { combination: { ...combination, nameEn: null }, locale: "en-GB", fallback: "Chemistry" })).toBe(
      "QD02 · Chemistry",
    );
  });
});

describe.each(["vi", "en"] as const)("nhãn đường theo cấu trúc (%s)", (lang) => {
  const options = lang === "en" ? { locale: "en", messages: en } : undefined;
  const expected = LABELS[lang];

  function expectLocalized(text: string, code: "QD02" | "QD01") {
    expect(text).toContain(expected[code]);
    // Mã chỉ xuất hiện một lần trong nhãn (QĐ02 hoặc QD02, không phải cả hai).
    expect(countOf(text.replace(/Đ/g, "D"), "QD0" + code.slice(-1))).toBe(1);
    if (lang === "en") expect(text).not.toMatch(VIETNAMESE);
  }

  it("Kiểm tra: 'Kết quả theo đường lựa chọn' (bảng, nút thao tác) và 'Chi tiết theo khối'", async () => {
    const wire = {
      ...checkResultWire("VALID"),
      groups: [],
      paths: PATHS.map((path) => ({ ...path, result: "VALID", issues: [] })),
    };
    const fake = server().on("GET", `${EXAM_PATH}/check`, { status: 200, body: wire });
    fake.install();
    renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />, options);

    const paths = await screen.findByRole("region", { name: lang === "en" ? "Results by choice path" : "Kết quả theo đường lựa chọn" });
    const table = within(paths).getAllByRole("table")[0]!;
    await vi.waitFor(() => expect(table.textContent).toContain(expected.QD02));
    for (const code of ["QD02", "QD01"] as const) expectLocalized(table.textContent ?? "", code);

    await vi.waitFor(() => expect(screen.getAllByRole("tab")[0]!.textContent).toContain(expected.QD02));
    const tabs = screen.getAllByRole("tab");
    const tabText = tabs.map((tab) => tab.textContent ?? "").join("|");
    for (const code of ["QD02", "QD01"] as const) expectLocalized(tabText, code);
  });

  it("Xem trước: ô chọn đường, tên đường trong lựa chọn và tiêu đề đề thử", async () => {
    const user = userEvent.setup();
    const exam = mapExamDetail(examDetailWire({ parts: BCA_PARTS as never }));
    const check = {
      ...checkResultWire("VALID"),
      groups: [],
      paths: PATHS.map((path) => ({ ...path, result: "VALID", partIds: [], blocks: [], subjects: [], selections: [], issues: [] })),
    } as unknown as ExamCheckResult;
    const generated = {
      examId: EXAM_ID,
      status: "GENERATED",
      version: null,
      seed: 1,
      generatedAt: "2026-11-17T01:00:00Z",
      previewId: "33333333-3333-3333-3333-333333333333",
      path: PATHS[0],
      compliance: [],
      droppedQuestions: [],
      paper: { totalQuestions: 0, totalPoints: 0, totalMinutes: 0, sharedDuration: false, hasManualGrading: false, checks: [], knowledgeStats: [], subjects: [], parts: [] },
    };
    const fake = server().on("POST", `${EXAM_PATH}/preview`, { status: 200, body: generated });
    fake.install();
    renderWithProviders(
      <ExamPreviewPanel
        exam={exam}
        check={check}
        initialPathId="p3:QD02"
        autoGenerate={false}
        version={null}
        onClearVersion={() => {}}
        onShowCheck={() => {}}
      />,
      options,
    );

    const select = await screen.findByRole("combobox", { name: lang === "en" ? "Choice path" : "Đường lựa chọn" });
    await vi.waitFor(() => expect(select.textContent).toContain(expected.QD02));
    expectLocalized(select.textContent ?? "", "QD02");

    await user.click(select);
    const listed = (await screen.findAllByRole("option")).map((option) => option.textContent ?? "").join("|");
    for (const code of ["QD02", "QD01"] as const) expectLocalized(listed, code);
    await user.keyboard("{Escape}");

    await user.click(screen.getByRole("button", { name: lang === "en" ? "Generate preview paper" : "Sinh đề xem trước" }));
    const title = await screen.findByText(new RegExp(`${lang === "en" ? "Trial paper" : "Đề thử"} · `));
    expectLocalized(title.textContent ?? "", "QD02");
  });
});
