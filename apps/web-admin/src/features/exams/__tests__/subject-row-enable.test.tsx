import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import type { SectionContext } from "../components/exam-structure-draft";
import { fitsForm } from "../subject-forms/fit";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { RowListForm } from "../subject-forms/row-list-form";
import { listSubjectFormPairs, patchSubjectFormEntry, subjectFormFor } from "../subject-forms/registry";
import { selectAllSubjectForms } from "../subject-forms/selection";
import type { ExamPart, ExamSection } from "../types";
import { block, part, section, standardFixtureTemplates, standardExam } from "./subject-form-builders";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

/**
 * B7 của lát CR-3a, trên mọi mẫu chuẩn của fixture B8: trước khi bật (cờ tắt) cặp mở MD, không dòng chữ; sau khi bật (cờ ở sản phẩm) cặp mở
 * đúng DS và vẽ đủ dòng; bài có tỷ lệ (ở kỳ không cho nhập %) rơi về MD kèm dòng chữ. Cờ ở sản phẩm là hàng `enabledRows` của \`registry.ts\`.
 */
const restores: Array<() => void> = [];
afterEach(() => {
  while (restores.length) restores.pop()!();
});

const ENABLED_KINDS = ["HSA", "QDA", "BCA", "THPTQG"] as const;
const isEnabledPair = (kind: string, subject: string) => subjectFormFor(kind, subject)?.enabled === true;

/** Mọi mục DS của cặp đã bật trong mọi mẫu chuẩn (cả `sections`, `subjectPool`, `options[].sections`). */
function enabledRowSections(): Array<{ code: string; kind: string; part: ExamPart; section: ExamSection }> {
  const found: Array<{ code: string; kind: string; part: ExamPart; section: ExamSection }> = [];
  for (const template of standardFixtureTemplates()) {
    for (const item of template.parts) {
      for (const candidate of [...item.sections, ...(item.subjectPool ?? []), ...item.options.flatMap((option) => option.sections)]) {
        if (isEnabledPair(template.kindCode, candidate.subjectCode)) found.push({ code: template.code, kind: template.kindCode, part: item, section: candidate });
      }
    }
  }
  return found;
}

describe("B7 lát CR-3a: trước và sau khi bật", () => {
  it("chỉ các cặp DS của HSA, QDA, BCA (VAN, ANH) và THPTQG đang bật; MT và DS của lát CR-3b còn tắt", () => {
    const enabled = listSubjectFormPairs().filter(({ entry }) => entry.enabled);
    expect(enabled.every(({ entry }) => entry.form === "ROWS")).toBe(true);
    expect(enabled.map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`).toSorted()).toEqual(
      [
        ...["HSA", "QDA", "BCA"].flatMap((kind) => ["VAN", "ANH"].map((subject) => `${kind}:${subject}`)),
        ...["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "ECON_LAW", "TECH_INDUSTRY", "TECH_AGRICULTURE", "INFORMATICS", "ANH", "VAN"].map((subject) => `THPTQG:${subject}`),
      ].toSorted(),
    );
  });

  it("trước khi bật: cờ tắt thì mọi mục DS của mọi mẫu mở MD, không dòng chữ; sau khi bật mở DS, không rơi về MD", () => {
    const rows = enabledRowSections();
    expect(rows.length).toBeGreaterThan(20);
    const kinds = new Set(rows.map((item) => item.kind));
    expect([...kinds].toSorted()).toEqual([...ENABLED_KINDS].toSorted());
    for (const { kind, part: owner, section: item, code } of rows) {
      const after = selectAllSubjectForms(standardExam(kind), [owner])[item.id];
      expect(after, `${code} ${item.subjectCode} sau khi bật`).toEqual({ form: "ROWS", notice: false });
      const undo = patchSubjectFormEntry(kind, item.subjectCode, { enabled: false });
      const before = selectAllSubjectForms(standardExam(kind), [owner])[item.id];
      undo();
      expect(before, `${code} ${item.subjectCode} trước khi bật`).toEqual({ form: "DEFAULT", notice: false });
    }
  });

  it("bài có tỷ lệ ở kỳ không cho nhập % mở MD kèm dòng chữ ở cả trước và sau khi bật (PERCENT_ENTRY)", () => {
    const percent = section("s", "VAN", [block("b", { matrix: null })], {
      matrix: { mode: "SEPARATE", unit: "PERCENT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 100 }] },
    });
    for (const kind of ENABLED_KINDS) {
      const entry = subjectFormFor(kind, "VAN")!;
      expect(fitsForm(percent, entry, { kindCode: kind, subjectCode: "VAN" })).toEqual({ fits: false, reason: "PERCENT_ENTRY" });
      expect(selectAllSubjectForms(standardExam(kind), [part("p", [percent])]).s).toEqual({ form: "DEFAULT", notice: true, reason: "PERCENT_ENTRY" });
    }
  });

  it("sau khi bật: DS thật vẽ đủ dòng của mọi mục DS trong mọi mẫu chuẩn của cặp bật, mỗi dòng gắn data-form-row", async () => {
    const reference: ContentReference = await getContentReferenceFixture();
    const axes = { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((difficulty) => difficulty.code) };
    const rows = enabledRowSections();
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
  });
});
