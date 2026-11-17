import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ExamPreviewPaper } from "@/features/exams/components/exam-preview-paper";
import type { PreviewPaper } from "@/features/exams/review-types";
import { QuestionPreview } from "@/features/question-import/components/question-preview";
import type { ImportQuestionDraft, ImportRowResult } from "@/features/question-import/types";
import { renderWithProviders } from "@/test/render";
import { createRef } from "react";
import { QuestionBodyView } from "../components/question-detail-body";
import { QuestionVersionsCompare } from "../components/question-versions-compare";
import type { QuestionVersion } from "../editor-types";
import { QuestionPlayer } from "../preview/question-player";
import { emptyAnswer } from "../preview/grading";
import type { QuestionDetail } from "../types";
import { fixtureAll } from "./api-fixtures";
import { renderEn } from "./editor-test-kit";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const PASSAGE =
  '<p>The word was <span class="blank" data-blank="1"></span> by Crutzen. It began <span class="blank" data-blank="2"></span> years ago.</p>';

/** Ngữ liệu có hai chỗ trống; `blankNumberStart` = 21 phải hiện (21) (22), không phải (1) (2) (ED-VG F1). */
async function expectNumberedFrom21(container: HTMLElement = document.body) {
  await waitFor(() => {
    const blanks = [...container.querySelectorAll(".blank")].map((node) => node.textContent);
    expect(blanks).toEqual(["(21) ____", "(22) ____"]);
  });
}

function set(blankNumberStart: number): QuestionDetail {
  const base = fixtureAll().find((question) => question.type === "QUESTION_SET");
  if (!base) throw new Error("fixture has no QUESTION_SET");
  return {
    ...base,
    content: PASSAGE,
    body: { type: "QUESTION_SET", passage: PASSAGE, blankNumberStart, childIds: base.children.map((child) => child.id) },
  };
}

describe("số chỗ trống theo blankNumberStart ở mọi nơi dựng ngữ liệu (ED-VG F1)", () => {
  it("chi tiết câu hỏi và hộp xem phiên bản (QuestionBodyView)", async () => {
    renderEn(<QuestionBodyView question={set(21)} />);
    await expectNumberedFrom21();
  });

  it("xem trước câu hỏi (QuestionPlayer)", async () => {
    const question = set(21);
    renderEn(<QuestionPlayer question={question} answer={emptyAnswer(question)} onChange={() => {}} result={null} />);
    await expectNumberedFrom21();
  });

  it("so sánh phiên bản dựng ngữ liệu của từng bên theo số bắt đầu của bên đó", async () => {
    const make = (version: number, content: string, blankNumberStart: number): QuestionVersion => {
      const question = set(blankNumberStart);
      return {
        version,
        state: "SUPERSEDED",
        savedAt: "2026-11-17T00:00:00Z",
        savedBy: { id: "u1", name: "A" } as QuestionVersion["savedBy"],
        attemptCount: 0,
        snapshot: {
          subjectCode: question.subjectCode,
          gradeCode: question.gradeCode,
          difficultyCode: question.difficultyCode,
          competencyCode: question.competencyCode,
          taskTypeCode: question.taskTypeCode,
          points: question.points,
          knowledge: question.knowledge,
          tagCodes: question.tagCodes,
          content,
          body: { ...(question.body as Extract<QuestionDetail["body"], { type: "QUESTION_SET" }>), blankNumberStart },
          solution: question.solution,
          commonMistakes: question.commonMistakes,
          attachmentIds: question.attachmentIds,
          children: question.children,
        },
      };
    };
    renderEn(<QuestionVersionsCompare before={make(1, "<p>Cũ</p>", 1)} after={make(2, PASSAGE, 21)} />);
    await waitFor(() => {
      const blanks = [...document.querySelectorAll(".blank")].map((node) => node.textContent);
      expect(blanks).toEqual(["(21) ____", "(22) ____"]);
    });
  });

  it("xem trước đề thi (ExamPreviewPaper)", async () => {
    const paper: PreviewPaper = {
      totalQuestions: 0,
      totalPoints: 0,
      totalMinutes: 0,
      sharedDuration: false,
      hasManualGrading: false,
      subjects: [],
      checks: [],
      knowledgeStats: [],
      parts: [
        {
          partId: "p1",
          name: "Part 1",
          order: 1,
          durationMinutes: null,
          firstNumber: 1,
          lastNumber: 2,
          points: 0,
          maxPoints: null,
          blocks: [
            {
              blockId: "b1",
              blockName: "Block",
              subjectCode: "ENGLISH",
              questionType: "QUESTION_SET",
              manualGrading: false,
              pointsEach: 1,
              firstNumber: 1,
              lastNumber: 2,
              questionCount: 2,
              setCount: 1,
              setSize: 2,
              requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] },
              allocation: [],
              items: [
                { kind: "SET", setId: "s1", code: "CH-1", version: 1, simulated: false, passage: PASSAGE, blankNumberStart: 21, children: [] },
              ],
            },
          ],
        },
      ],
    };
    renderEn(<ExamPreviewPaper paper={paper} />);
    await expectNumberedFrom21();
  });

  it("xem trước dòng nhập (QuestionPreview)", async () => {
    const draft = {
      type: "QUESTION_SET",
      content: PASSAGE,
      blankNumberStart: 21,
      options: [],
      statements: [],
      acceptedAnswers: [],
      scoringRule: null,
      trueFalseScoring: null,
      blanks: [],
      matching: null,
      commonMistakes: null,
      taskTypeCode: null,
      points: null,
      inputHint: null,
      gradingGuide: null,
      rubric: [],
      sampleAnswer: null,
      solution: null,
      subjectCode: null,
      gradeCode: null,
      difficultyCode: null,
      knowledge: [],
      tagCodes: [],
      setCode: null,
    } satisfies ImportQuestionDraft;
    const row: ImportRowResult = {
      rowNumber: 2,
      sourceCode: null,
      status: "VALID",
      issues: [],
      duplicateOf: null,
      isSetPassage: true,
      parentRowNumber: null,
      draft,
    };
    renderWithProviders(
      <QuestionPreview row={row} rows={[row]} reference={undefined} headingRef={createRef<HTMLHeadingElement>()} describe={() => ({ message: "", hint: "" }) as never} />,
    );
    await expectNumberedFrom21();
  });
});
