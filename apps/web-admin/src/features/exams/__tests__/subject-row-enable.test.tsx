import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import type { SectionContext } from "../components/exam-structure-draft";
import { fitsForm } from "../subject-forms/fit";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { RowListForm } from "../subject-forms/row-list-form";
import { listSubjectFormPairs, subjectFormFor } from "../subject-forms/registry";
import { selectAllSubjectForms } from "../subject-forms/selection";
import type { ExamPart, ExamSection } from "../types";
import { block, part, section, standardFixtureTemplates, standardExam } from "./subject-form-builders";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const ENABLED_KINDS = ["HSA", "QDA", "BCA", "THPTQG", "VACT", "SP2E", "SPT", "HSCA", "VSAT"] as const;
const isRowPair = (kind: string, subject: string) => subjectFormFor(kind, subject)?.form === "ROWS";

/** Mọi mục DS trong mọi mẫu chuẩn (cả `sections`, `subjectPool`, `options[].sections`). */
function rowSections(): Array<{ code: string; kind: string; part: ExamPart; section: ExamSection }> {
  const found: Array<{ code: string; kind: string; part: ExamPart; section: ExamSection }> = [];
  for (const template of standardFixtureTemplates()) {
    for (const item of template.parts) {
      for (const candidate of [...item.sections, ...(item.subjectPool ?? []), ...item.options.flatMap((option) => option.sections)]) {
        if (isRowPair(template.kindCode, candidate.subjectCode)) found.push({ code: template.code, kind: template.kindCode, part: item, section: candidate });
      }
    }
  }
  return found;
}

describe("Phủ bảng đăng ký và chọn DS trên mọi mẫu chuẩn", () => {
  it("mọi cặp DS và MT (HSA, QDA, BCA, V-ACT) đăng ký đúng dạng", () => {
    const pairs = listSubjectFormPairs();
    const rowPairs = pairs.filter(({ entry }) => entry.form === "ROWS");
    const matrixPairs = pairs.filter(({ entry }) => entry.form === "MATRIX");
    expect(rowPairs.map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`).toSorted()).toEqual(
      [
        ...["HSA", "QDA", "BCA"].flatMap((kind) => ["VAN", "ANH"].map((subject) => `${kind}:${subject}`)),
        ...["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW", "TECH_INDUSTRY", "TECH_AGRICULTURE", "INFORMATICS", "ANH", "VAN"].map((subject) => `THPTQG:${subject}`),
        ...["ANH", "SCIENTIFIC_THINKING"].map((subject) => `VACT:${subject}`),
        ...["VAN", "LY", "HOA", "SINH", "DIA", "SU", "ECON_LAW", "ANH"].map((subject) => `SP2E:${subject}`),
        ...["LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW", "TECH_INDUSTRY", "TECH_AGRICULTURE", "INFORMATICS", "ANH", "VAN"].map((subject) => `SPT:${subject}`),
        ...["TOAN", "LY", "HOA", "SINH", "VAN", "ANH"].map((subject) => `HSCA:${subject}`),
        ...["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN", "ANH"].map((subject) => `VSAT:${subject}`),
      ].toSorted(),
    );
    expect(matrixPairs.map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`).toSorted()).toEqual(
      [
        ...["HSA", "QDA", "BCA"].flatMap((kind) => ["TOAN", "LY", "HOA", "SINH", "SU", "DIA"].map((subject) => `${kind}:${subject}`)),
        "VACT:TOAN",
        "VACT:VIETNAMESE",
        "SP2E:TOAN",
        "SPT:TOAN",
      ].toSorted(),
    );
  });

  it("mọi mục DS của mọi mẫu mở DS, không rơi về MD", () => {
    const rows = rowSections();
    expect(rows.length).toBeGreaterThan(20);
    const kinds = new Set(rows.map((item) => item.kind));
    expect([...kinds].toSorted()).toEqual([...ENABLED_KINDS].toSorted());
    for (const { kind, part: owner, section: item, code } of rows) {
      const picked = selectAllSubjectForms(standardExam(kind), [owner])[item.id];
      expect(picked, `${code} ${item.subjectCode}`).toEqual({ form: "ROWS", notice: false });
    }
  });

  it.each([["SP2E", "LY"], ["HSCA", "ANH"], ["VACT", "SCIENTIFIC_THINKING"], ["VSAT", "TOAN"]])(
    "bài % của %s %s mở DS",
    (kind, subject) => {
      const item = section("s", subject, [block("b")], {
        matrix: { mode: "SEPARATE", unit: "PERCENT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 100 }] },
      });
      const owner = part("p", [item]);
      expect(selectAllSubjectForms(standardExam(kind), [owner]).s).toEqual({ form: "ROWS", notice: false });
    },
  );

  it("bài có tỷ lệ ở kỳ không cho nhập % mở MD kèm dòng chữ (PERCENT_ENTRY)", () => {
    const percent = section("s", "VAN", [block("b", { matrix: null })], {
      matrix: { mode: "SEPARATE", unit: "PERCENT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 100 }] },
    });
    for (const kind of ["HSA", "QDA", "BCA", "THPTQG"]) {
      const entry = subjectFormFor(kind, "VAN")!;
      expect(fitsForm(percent, entry, { kindCode: kind, subjectCode: "VAN" })).toEqual({ fits: false, reason: "PERCENT_ENTRY" });
      expect(selectAllSubjectForms(standardExam(kind), [part("p", [percent])]).s).toEqual({ form: "DEFAULT", notice: true, reason: "PERCENT_ENTRY" });
    }
  });

  it("DS thật vẽ đủ dòng của mọi mục DS trong mọi mẫu chuẩn, mỗi dòng gắn data-form-row", async () => {
    const reference: ContentReference = await getContentReferenceFixture();
    const axes = { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((difficulty) => difficulty.code) };
    const rows = rowSections();
    for (const { code, kind, part: owner, section: item } of rows) {
      const context: SectionContext = { part: owner, option: null, section: item, location: { partId: owner.id, optionId: null, sectionId: item.id } };
      const view = renderWithProviders(
        <RowListForm
          context={context}
          subjectName={item.subjectCode}
          axes={axes}
          labels={{ grade: (value) => value, difficulty: (value) => value }}
          reference={reference}
          onChange={() => undefined}
          examId="exam-1"
          examKind={kind}
          rawInputs={createRawInputStore()}
        />,
      );
      const ids = Array.from(view.container.querySelectorAll("[data-form-row]")).map((row) => row.getAttribute("data-form-row"));
      expect(ids, `${code} ${item.subjectCode}`).toEqual(item.blocks.map((row) => row.id));
      view.unmount();
    }
    expect(screen.queryByRole("heading", { name: "Các dòng lấy câu" })).toBeNull();
    expect(within(document.body).queryAllByRole("table")).toHaveLength(0);
  }, 30000);
});
