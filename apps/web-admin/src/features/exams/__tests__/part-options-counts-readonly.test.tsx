import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { PartOptions } from "../components/part-options";
import type { ExamPart, ExamPartOption, ExamSection } from "../types";

/** UNI-2753 (R1-F5, AD-28): số câu từng môn trong thẻ lựa chọn (nhánh) của phần tự chọn là số của bước 1, chỉ đọc ở bước 2. */

const section = (id: string, subjectCode: string, questionCount: number): ExamSection =>
  ({ id, subjectCode, questionCount, blocks: [], knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: null }) as unknown as ExamSection;

const BRANCH: ExamPartOption = {
  id: "opt-br-1",
  kind: "BRANCH",
  code: "BR-1",
  name: "Lập trình",
  chooseSubjects: null,
  countsByPick: null,
  sections: [section("s-inf", "INFORMATICS", 2)],
};

const PART = {
  id: "part-3",
  mode: "CHOICE",
  name: "Phần 3",
  questionCount: 4,
  codeSource: null,
  sections: [section("s-common", "COMMON", 2)],
  options: [BRANCH],
} as unknown as ExamPart;

const names = { subject: (code: string) => code, combination: (code: string) => code };

function renderOptions() {
  return renderWithProviders(
    <PartOptions
      part={PART}
      examId="exam-1"
      names={names}
      issues={[]}
      showEnglish={false}
      branchSubjects={[]}
      onToggleEnglish={vi.fn()}
      onAddBranch={vi.fn()}
      onConfirm={vi.fn()}
      onRemove={vi.fn()}
      onEditBlocks={vi.fn()}
    />,
  );
}

describe("thẻ lựa chọn của phần tự chọn: số câu từng môn chỉ đọc (R1-F5)", () => {
  it("ô số câu của môn trong nhánh là chỉ đọc, giữ số của bước 1", () => {
    renderOptions();
    const field = screen.getByRole("textbox", { name: "Lập trình · INFORMATICS · số câu" });
    expect(field).toHaveValue("2");
    expect(field).toHaveAttribute("readonly");
    expect(field).toHaveAttribute("aria-readonly", "true");
  });

  it("có đường dẫn 'Sửa thông tin tổng quan' về bước Thông tin chung của bài", () => {
    renderOptions();
    expect(screen.getByRole("link", { name: "Sửa thông tin tổng quan" })).toHaveAttribute("href", "/exams/exam-1");
  });
});
