import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { ExamStructureSummaryTable } from "../components/exam-structure-summary";
import { optionLabel, type StructureNames, useStructureIssueText } from "../components/exam-structure-issues";
import type { StructureIssue } from "../matrix";
import type { ExamPart, ExamSection, ExamStructureSummaryRow } from "../types";

/** Tên danh mục (môn) theo ngôn ngữ đang chọn ở màn Cấu trúc: dòng môn của Tổng hợp và dòng lỗi (V-05, V-06). */

const NAMES: Record<"en" | "vi", Record<string, string>> = {
  en: { MATH: "Mathematics", ENGLISH: "English" },
  vi: { MATH: "Toán", ENGLISH: "Tiếng Anh" },
};

const namesFor = (locale: "en" | "vi"): StructureNames => ({
  subject: (code) => NAMES[locale][code] ?? code,
  combination: (code) => code,
});

function renderIn(locale: "en" | "vi", ui: ReactElement) {
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "en" ? enMessages : viMessages} timeZone="UTC">
      {ui}
    </NextIntlClientProvider>,
  );
}

const row = (over: Partial<ExamStructureSummaryRow>): ExamStructureSummaryRow => ({
  name: null,
  partId: null,
  sectionId: null,
  blockId: null,
  target: null,
  allocated: null,
  difference: null,
  essayCount: null,
  objectiveCount: null,
  setCount: null,
  commandCount: null,
  childCount: null,
  maxPoints: null,
  ...over,
});

// Máy chủ đặt `name` của dòng môn là mã môn; dòng phần mang tên của mẫu, dòng nhóm mang tên nhóm của mẫu.
const SUMMARY_ROWS = [
  row({ name: "Toán và xử lí số liệu", partId: "p1", target: 50, allocated: 50 }),
  row({ name: "MATH", partId: "p1", sectionId: "s1", target: 50, allocated: 50 }),
  row({ name: "Một đáp án", partId: "p1", sectionId: "s1", blockId: "b1", target: 50, allocated: 50 }),
];

function Summary({ locale }: { locale: "en" | "vi" }) {
  return (
    <ExamStructureSummaryTable
      summary={{ rows: SUMMARY_ROWS }}
      load="ready"
      dirty={false}
      names={namesFor(locale)}
      onSelect={vi.fn()}
    />
  );
}

describe("Tổng hợp: tên môn theo danh mục (V-06)", () => {
  it.each([
    ["en", "Mathematics"],
    ["vi", "Toán"],
  ] as const)("%s: dòng môn hiện tên danh mục, không hiện mã thô; tên phần và nhóm của mẫu giữ nguyên", (locale, subject) => {
    renderIn(locale, <Summary locale={locale} />);
    const table = screen.getByRole("table");
    const headers = within(table)
      .getAllByRole("rowheader")
      .map((cell) => cell.textContent);
    expect(headers).toEqual(["Toán và xử lí số liệu", subject, "Một đáp án"]);
    expect(within(table).queryByText("MATH")).not.toBeInTheDocument();
  });
});

const section = (over: Partial<ExamSection> = {}): ExamSection => ({
  id: "s1",
  subjectCode: "MATH",
  questionCount: 5,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
  ...over,
});

// Tên phần của mẫu tình cờ trùng tên môn tiếng Việt: câu lỗi không được ghép tên phần với tên danh mục ("Toán · Mathematics").
const PART: ExamPart = {
  id: "p1",
  order: 1,
  name: "Toán",
  durationMinutes: 60,
  questionCount: 5,
  questionCountOnPaper: 5,
  questionCountRequired: 5,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [section()],
  options: [],
};

const ISSUE: StructureIssue = { code: "BLOCKS_TOTAL", location: { partId: "p1", optionId: null, sectionId: "s1" }, expected: 5, actual: 3 };

function IssueLine({ locale }: { locale: "en" | "vi" }) {
  const text = useStructureIssueText([PART], namesFor(locale));
  return <p>{text(ISSUE)}</p>;
}

describe("Dòng lỗi cấu trúc: môn có mã môn chỉ dùng tên danh mục (V-05)", () => {
  it("en: không ghép tên phần của mẫu với tên môn", () => {
    renderIn("en", <IssueLine locale="en" />);
    expect(screen.getByText("Mathematics: the blocks have 3 questions; the subject needs 5.")).toBeInTheDocument();
  });

  it("vi: tên môn tiếng Việt", () => {
    renderIn("vi", <IssueLine locale="vi" />);
    expect(screen.getByText(/^Toán: /)).toBeInTheDocument();
    expect(screen.queryByText(/Toán · Toán/)).not.toBeInTheDocument();
  });
});

describe("Nhãn phương án tổ hợp (V-05)", () => {
  const names: StructureNames = {
    subject: (code) => code,
    combination: (code) => (code === "KH-1" ? "Physics – Chemistry – Biology" : code),
  };

  it("tổ hợp có trong danh mục: tên danh mục theo ngôn ngữ thắng tên đã lưu của phương án", () => {
    expect(optionLabel({ kind: "COMBINATION", code: "KH-1", name: "Vật lí – Hóa học – Sinh học" }, names)).toBe("KH-1 · Physics – Chemistry – Biology");
  });

  it("tổ hợp chưa có trong danh mục: giữ tên đã lưu", () => {
    expect(optionLabel({ kind: "COMBINATION", code: "X-9", name: "Tên đã lưu" }, names)).toBe("X-9 · Tên đã lưu");
  });
});
