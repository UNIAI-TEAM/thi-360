import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockSetConditions } from "../components/block-set-conditions";
import type { SectionContext } from "../components/exam-structure-draft";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { ReferenceQuotas, referenceQuotaEntries } from "../subject-forms/reference-quotas";
import { subjectFormFor } from "../subject-forms/registry";
import { RowEditor } from "../subject-forms/row-editor";
import { RowListForm } from "../subject-forms/row-list-form";
import { RowSplitPanel } from "../subject-forms/row-split-panel";
import type { ExamPart, ExamQuota, ExamSection, QuestionBlock } from "../types";
import { part as buildPart, section as buildSection } from "./subject-form-builders";

/**
 * V2 + V3: bản `en` bọc mọi tên nội dung đề (tên dòng, phân đoạn, nhãn chùm, tên chỉ tiêu tham khảo) trong `[data-exam-content]`,
 * dùng `aria-labelledby` thay cho `aria-label` chèn tên tiếng Việt, và chia số ít / số nhiều đúng ngữ pháp tiếng Anh.
 */
const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i;
const UI_WORDS = /\b(Edit|Set|Conditions|Split|Distribution|Question rows)\b/;

function vietnameseOutsideExamContent(root: HTMLElement): string[] {
  const leaks: string[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const text = node.textContent ?? "";
    if (VIETNAMESE.test(text) && !node.parentElement?.closest("[data-exam-content]")) leaks.push(text.trim());
  }
  for (const element of root.querySelectorAll("[aria-label],[title],[placeholder],[alt]")) {
    if (element.closest("[data-exam-content]")) continue;
    for (const name of ["aria-label", "title", "placeholder", "alt"]) {
      const value = element.getAttribute(name);
      if (value && VIETNAMESE.test(value)) leaks.push(`${name}=${value}`);
    }
  }
  return leaks;
}

function examContentTexts(root: HTMLElement): string[] {
  return [...root.querySelectorAll("[data-exam-content]")].map((element) => element.textContent ?? "");
}

async function makeReference(): Promise<ContentReference> {
  const base = await getContentReferenceFixture();
  return {
    ...base,
    tags: [
      { code: "TAG_NLXH", name: "Nghị luận xã hội" },
      { code: "TAG_VBTT", name: "Văn bản thông tin – nghị luận" },
    ],
  };
}

const AXES = { grades: ["GRADE_10", "GRADE_11", "GRADE_12"], difficulties: ["RECOGNITION", "COMPREHENSION"] };
const LABELS = { grade: (code: string) => code, difficulty: (code: string) => code };

function sampleBlocks(): QuestionBlock[] {
  return [
    {
      id: "row-1",
      name: "Từ khác nhóm nghĩa",
      questionType: "SINGLE_CHOICE",
      questionCount: 1,
      setCount: null,
      setSize: null,
      requiredPointsPerQuestion: 1,
      knowledgeScope: null,
      tagCodes: [],
      matrix: null,
      childAnswerType: null,
      templateGroupCode: null,
      answerTypeLocked: false,
      positionRange: { from: 1, to: 1 },
    },
    {
      id: "row-2",
      name: "Đọc hiểu",
      questionType: "QUESTION_SET",
      questionCount: 5,
      setCount: 1,
      setSize: 5,
      requiredPointsPerQuestion: 2,
      knowledgeScope: null,
      tagCodes: [],
      matrix: null,
      childAnswerType: "SINGLE_CHOICE",
      templateGroupCode: null,
      answerTypeLocked: false,
      shuffleEditable: true,
      positionRange: { from: 2, to: 6 },
      slots: [
        { index: 1, tagCodes: ["TAG_NLXH"] },
        { index: 2, tagCodes: ["TAG_VBTT"] },
      ],
    },
    {
      id: "row-3",
      name: "Nghe – chùm 4 câu",
      questionType: "QUESTION_SET",
      questionCount: 1,
      setCount: 1,
      setSize: 1,
      requiredPointsPerQuestion: 1,
      knowledgeScope: null,
      tagCodes: [],
      matrix: null,
      childAnswerType: "SINGLE_CHOICE",
      templateGroupCode: null,
      answerTypeLocked: false,
      positionRange: { from: 7, to: 7 },
    },
    {
      id: "row-4",
      name: "Câu tổng hợp",
      questionType: "QUESTION_SET",
      questionCount: 25,
      setCount: 5,
      setSize: 5,
      requiredPointsPerQuestion: 1.5,
      knowledgeScope: null,
      tagCodes: [],
      matrix: null,
      childAnswerType: "SINGLE_CHOICE",
      templateGroupCode: null,
      answerTypeLocked: false,
      positionRange: { from: 8, to: 32 },
    },
  ];
}

function sampleSectionAndPart(blocks: QuestionBlock[]): { part: ExamPart; section: ExamSection } {
  const section = buildSection("sec-van", "VAN", blocks);
  const part = buildPart("part-1", [section], {
    name: "Phần I · Đọc hiểu",
    displaySegments: [{ code: "SEG_1", name: "Phân đoạn Đọc hiểu", fromPosition: 1, toPosition: 32, commandCount: 32, maxPoints: 32 }],
  });
  return { part, section };
}

describe("CR-3 bản en: data-exam-content, aria-labelledby và số ít/số nhiều", () => {
  it("RowListForm bản en bọc tên phân đoạn, tên dòng, nhãn chùm trong [data-exam-content] và chia số ít/nhiều đúng", async () => {
    const reference = await makeReference();
    const blocks = sampleBlocks();
    const { part, section } = sampleSectionAndPart(blocks);
    const context: SectionContext = { part, option: null, section, location: { partId: part.id, optionId: null, sectionId: section.id } };

    const { container } = renderWithProviders(
      <RowListForm
        context={context}
        subjectName="Literature"
        axes={AXES}
        labels={LABELS}
        reference={reference}
        onChange={() => undefined}
        examId="exam-1"
        examKind="HSA"
        rawInputs={createRawInputStore()}
      />,
      { locale: "en", messages: en },
    );

    expect(vietnameseOutsideExamContent(container)).toEqual([]);

    const content = examContentTexts(container);
    expect(content).toContain("Phân đoạn Đọc hiểu");
    const segmentRow = container.querySelector('[data-form-segment="SEG_1"]')!;
    expect(segmentRow.querySelector("th")).toBeNull();
    expect(segmentRow.querySelector("td[colspan]")).toHaveAttribute("colspan", "5");
    expect(content).toContain("Từ khác nhóm nghĩa");
    expect(content).toContain("Đọc hiểu");
    expect(content).toContain("Nghị luận xã hội");
    expect(content).toContain("Văn bản thông tin – nghị luận");
    expect(content.filter((text) => UI_WORDS.test(text))).toEqual([]);

    const editBtn = screen.getByRole("button", { name: "Edit row Từ khác nhóm nghĩa" });
    expect(editBtn.getAttribute("aria-label")).toBe("Edit row");

    const slotsList = screen.getByRole("list", { name: "Conditions per set of Đọc hiểu" });
    expect(slotsList.getAttribute("aria-label")).toBe("Conditions per set of");
    expect(within(slotsList).getAllByRole("listitem")[0]).toHaveTextContent("Set 1: Nghị luận xã hội");

    // V3: số ít với 1 và số nhiều với > 1.
    const row1 = container.querySelector<HTMLElement>('[data-form-row="row-1"]')!;
    expect(within(row1).getByText("1 question")).toBeInTheDocument();
    expect(within(row1).getByText("1 question · 1 point")).toBeInTheDocument();

    const row2 = container.querySelector<HTMLElement>('[data-form-row="row-2"]')!;
    expect(within(row2).getByText("1 set × 5 (5 questions)")).toBeInTheDocument();
    expect(within(row2).getByText("5 questions · 10 points")).toBeInTheDocument();

    const row3 = container.querySelector<HTMLElement>('[data-form-row="row-3"]')!;
    expect(within(row3).getByText("1 set × 1 (1 question)")).toBeInTheDocument();

    const row4 = container.querySelector<HTMLElement>('[data-form-row="row-4"]')!;
    expect(within(row4).getByText("5 sets × 5 (25 questions)")).toBeInTheDocument();
  });

  it("RowEditor và RowSplitPanel bản en không rò tiếng Việt ngoài [data-exam-content] và chia point/points", async () => {
    const user = userEvent.setup();
    const reference = await makeReference();
    const blocks = sampleBlocks();
    const { section } = sampleSectionAndPart(blocks);
    const entry = subjectFormFor("HSCA", "ANH")!;

    const first = renderWithProviders(
      <RowEditor
        block={blocks[2]!}
        section={section}
        kindCode="HSCA"
        entry={entry}
        position={3}
        total={4}
        context={{ scope: "SUBJECT", reviewMode: false, template: null }}
        template={null}
        axes={AXES}
        labels={LABELS}
        reference={reference}
        topicError={null}
        onChange={() => undefined}
        onClose={() => undefined}
        focusNonce={0}
        focusField={null}
        sectionCount={32}
        rawInputs={createRawInputStore()}
        split={{ status: "OPEN", units: 4 }}
        onSplit={() => undefined}
      />,
      { locale: "en", messages: en },
    );

    expect(screen.getByRole("heading", { name: "Edit row: Nghe – chùm 4 câu" })).toBeInTheDocument();
    expect(screen.getByText(/1 point per question/)).toBeInTheDocument();
    expect(vietnameseOutsideExamContent(first.container)).toEqual([]);

    await user.click(screen.getByRole("button", { name: "Split row" }));
    const splitSection = document.querySelector<HTMLElement>("[data-row-split]")!;
    expect(splitSection.getAttribute("aria-label")).toBeNull();
    expect(screen.getByRole("region", { name: "Split row Nghe – chùm 4 câu" })).toBe(splitSection);
    expect(within(splitSection).getByRole("heading", { name: "Split row Nghe – chùm 4 câu" }).querySelector("[data-exam-content]")?.textContent).toBe("Nghe – chùm 4 câu");
    expect(vietnameseOutsideExamContent(first.container)).toEqual([]);
    first.unmount();

    // Kiểm số nhiều của points per question và RowSplitPanel đứng riêng.
    const second = renderWithProviders(
      <>
        <RowEditor
          block={{ ...blocks[1]!, requiredPointsPerQuestion: 2 }}
          section={section}
          kindCode="HSCA"
          entry={entry}
          position={2}
          total={4}
          context={{ scope: "SUBJECT", reviewMode: false, template: null }}
          template={null}
          axes={AXES}
          labels={LABELS}
          reference={reference}
          topicError={null}
          onChange={() => undefined}
          onClose={() => undefined}
          focusNonce={0}
          focusField={null}
          sectionCount={32}
          rawInputs={createRawInputStore()}
          split={{ status: "HIDDEN" }}
          onSplit={() => undefined}
        />
        <RowSplitPanel
          block={blocks[2]!}
          units={4}
          sectionId={section.id}
          rawInputs={createRawInputStore()}
          onApply={() => undefined}
          onCancel={() => undefined}
        />
      </>,
      { locale: "en", messages: en },
    );
    expect(screen.getByText(/2 points per question/)).toBeInTheDocument();
    expect(vietnameseOutsideExamContent(second.container)).toEqual([]);
  });

  it("ReferenceQuotas và BlockSetConditions bản en bọc tên tiếng Việt trong [data-exam-content], không rò vào aria-label", async () => {
    const reference = await makeReference();
    const quotas: ExamQuota[] = [
      {
        id: "q-grade",
        name: "Phân bố lớp tham khảo theo lệnh",
        dimension: "GRADE",
        basis: "PROMPT",
        enforcement: "REFERENCE",
        subjectCodes: ["TOAN"],
        buckets: [{ codes: ["GRADE_12"], count: 50 }],
      },
      {
        id: "q-diff",
        name: "Phân bố độ khó tham khảo",
        dimension: "DIFFICULTY",
        basis: "QUESTION",
        enforcement: "REFERENCE",
        subjectCodes: ["TOAN"],
        buckets: [{ codes: ["RECOGNITION"], count: 25 }],
      },
    ];
    const entries = referenceQuotaEntries("TOAN", quotas, true);
    const blockWithSlots = { ...sampleBlocks()[1]!, setCount: 2, questionCount: 10 };

    const { container } = renderWithProviders(
      <>
        <ReferenceQuotas entries={entries} labels={LABELS} subjectName="Mathematics" onApply={() => undefined} />
        <BlockSetConditions block={blockWithSlots} reference={reference} subjectCode="VAN" onChange={() => undefined} show="slots" />
      </>,
      { locale: "en", messages: en },
    );

    expect(vietnameseOutsideExamContent(container)).toEqual([]);

    const content = examContentTexts(container);
    expect(content).toContain("Phân bố lớp tham khảo theo lệnh");
    expect(content).toContain("Phân bố độ khó tham khảo");
    expect(content).toContain("Nghị luận xã hội");
    expect(content).toContain("Văn bản thông tin – nghị luận");

    expect(screen.getByRole("list", { name: "Phân bố lớp tham khảo theo lệnh" }).getAttribute("aria-label")).toBeNull();
    expect(screen.getByRole("group", { name: "Enforcement of grade distribution" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Enforcement of difficulty distribution" })).toBeInTheDocument();
  });
});
