import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { mapParts, toApiParts } from "../api-mapping";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import type { ExamPart, ExamSection, KnowledgeScope } from "../types";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSubjectAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

const NODE_ID = reference.knowledgeTree[0]!.children![0]!.id;
const BOX = "Mọi câu con thuộc chủ đề";

function makeSection(knowledgeScope: KnowledgeScope | null): ExamSection {
  return {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 30,
    knowledgeScope,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [],
  };
}

function makePart(section: ExamSection): ExamPart {
  return { id: "p1", sections: [section], options: [] } as unknown as ExamPart;
}

/** Giữ môn trong state như `ExamStructureView` và ghi lại môn mới nhất để kiểm tra giá trị lưu. */
function renderPanel(initial: ExamSection) {
  const latest = { section: initial };
  function Harness() {
    const [section, setSection] = useState(initial);
    return (
      <ExamStructureSubjectPanel
        examId="exam-1"
        context={{ part: makePart(section), option: null, section, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
        subjectName="Toán học"
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        onChange={(update) => {
          const next = update(section);
          latest.section = next;
          setSection(next);
        }}
        onSelectBlock={() => {}}
        onAddBlock={() => {}}
      />
    );
  }
  renderWithProviders(<Harness />);
  return latest;
}

function scopeField() {
  return screen.getByText("Phạm vi kiến thức").closest("fieldset")!;
}

/** Lưu rồi tải lại như màn: `toApiParts` gửi lên, `mapParts` đọc về. */
function reloadedSection(section: ExamSection): ExamSection {
  const sent = toApiParts([makePart(section)]);
  return mapParts(sent)[0]!.sections[0]!;
}

describe("ô Mọi câu con thuộc chủ đề ở phạm vi của môn", () => {
  it("môn chưa chọn MAPID không có ô", () => {
    renderPanel(makeSection(null));
    expect(screen.queryByRole("checkbox", { name: BOX })).not.toBeInTheDocument();
  });

  it("môn có MAPID: bật gửi everyChildInScope = true, tắt thì bỏ cờ", async () => {
    const user = userEvent.setup();
    const latest = renderPanel(makeSection({ nodeIds: [NODE_ID] }));
    const box = within(scopeField()).getByRole("checkbox", { name: BOX });
    expect(box).not.toBeChecked();
    await user.click(box);
    expect(latest.section.knowledgeScope).toEqual({ nodeIds: [NODE_ID], everyChildInScope: true });
    expect(box).toBeChecked();
    await user.click(box);
    expect(latest.section.knowledgeScope).toEqual({ nodeIds: [NODE_ID] });
    expect(box).not.toBeChecked();
  });

  it("môn từ mẫu đã có cờ true: ô bật sẵn; lưu rồi tải lại giữ nguyên cờ", () => {
    const section = makeSection({ nodeIds: [NODE_ID], everyChildInScope: true });
    renderPanel(section);
    expect(within(scopeField()).getByRole("checkbox", { name: BOX })).toBeChecked();
    expect(reloadedSection(section).knowledgeScope).toEqual({ nodeIds: [NODE_ID], everyChildInScope: true });
  });

  it("bật ô rồi lưu và tải lại qua api-mapping: cờ true còn, môn chưa bật không gửi cờ", async () => {
    const user = userEvent.setup();
    const latest = renderPanel(makeSection({ nodeIds: [NODE_ID] }));
    expect(reloadedSection(latest.section).knowledgeScope).toEqual({ nodeIds: [NODE_ID] });
    await user.click(within(scopeField()).getByRole("checkbox", { name: BOX }));
    const sent = toApiParts([makePart(latest.section)]) as unknown as { sections: { knowledgeScope: unknown }[] }[];
    expect(sent[0]!.sections[0]!.knowledgeScope).toEqual({ nodeIds: [NODE_ID], everyChildInScope: true });
    expect(reloadedSection(latest.section).knowledgeScope).toEqual({ nodeIds: [NODE_ID], everyChildInScope: true });
  });

  it("bỏ chip MAPID cuối cùng và chọn Toàn môn: cờ true còn qua lưu và tải lại dù ô ẩn", async () => {
    const user = userEvent.setup();
    const removeLast = renderPanel(makeSection({ nodeIds: [NODE_ID], everyChildInScope: true }));
    await user.click(screen.getByRole("button", { name: /^Bỏ kiến thức 0D1 / }));
    expect(removeLast.section.knowledgeScope).toEqual({ nodeIds: [], everyChildInScope: true });
    expect(screen.queryByRole("checkbox", { name: BOX })).not.toBeInTheDocument();
    expect(reloadedSection(removeLast.section).knowledgeScope).toEqual({ nodeIds: [], everyChildInScope: true });
  });

  it("chọn Toàn môn: cờ true còn; môn không cờ về null như trước", async () => {
    const user = userEvent.setup();
    const flagged = renderPanel(makeSection({ nodeIds: [NODE_ID], everyChildInScope: true }));
    await user.click(within(scopeField()).getByRole("radio", { name: "Toàn môn" }));
    expect(flagged.section.knowledgeScope).toEqual({ nodeIds: [], everyChildInScope: true });
    expect(reloadedSection(flagged.section).knowledgeScope).toEqual({ nodeIds: [], everyChildInScope: true });
  });

  it("môn không cờ chọn Toàn môn thì phạm vi về null", async () => {
    const user = userEvent.setup();
    const plain = renderPanel(makeSection({ nodeIds: [NODE_ID] }));
    await user.click(within(scopeField()).getByRole("radio", { name: "Toàn môn" }));
    expect(plain.section.knowledgeScope).toBeNull();
  });
});
