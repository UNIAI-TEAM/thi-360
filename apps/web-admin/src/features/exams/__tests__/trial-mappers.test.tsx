import { describe, expect, it } from "vitest";
import {
  buildSetAnswerValue,
  computeObjectiveTotals,
  computePartScoreSummaries,
  extractClozeResultEntries,
  sliceTrialPartSegments,
  toApiTrialAnswers,
  trialPartKey,
} from "../trial/mappers";
import type { PreviewGradeResponse, PreviewTrialPaperResponse, PreviewTrialPart } from "../trial/types";

describe("trial mappers (R1-4, R1-5, R1-14, R1-17, R1-33, R1-38, R1-39)", () => {
  it("toApiTrialAnswers chuyển đổi đủ 8 dạng câu và hỗ trợ xóa đáp án con trong QUESTION_SET", () => {
    const paper: PreviewTrialPaperResponse = {
      examId: "ex-1",
      previewId: "pv-1",
      parts: [
        {
          partId: "part-1",
          order: 1,
          blocks: [
            {
              blockId: "b1",
              items: [
                {
                  kind: "QUESTION",
                  question: { questionId: "q-sc", number: 1, type: "SINGLE_CHOICE", body: { type: "SINGLE_CHOICE" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-mc", number: 2, type: "MULTIPLE_CHOICE", body: { type: "MULTIPLE_CHOICE" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-sa", number: 3, type: "SHORT_ANSWER", body: { type: "SHORT_ANSWER" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-cl", number: 4, type: "CLOZE", body: { type: "CLOZE" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-tf", number: 5, type: "TRUE_FALSE", body: { type: "TRUE_FALSE" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-mt", number: 6, type: "MATCHING", body: { type: "MATCHING" } as never },
                },
                {
                  kind: "QUESTION",
                  question: { questionId: "q-es", number: 7, type: "ESSAY", body: { type: "ESSAY" } as never },
                },
                {
                  kind: "SET",
                  set: {
                    questionId: "q-set",
                    children: [
                      { questionId: "q-c1", number: 8, type: "SINGLE_CHOICE", body: { type: "SINGLE_CHOICE" } as never },
                      { questionId: "q-c2", number: 9, type: "SHORT_ANSWER", body: { type: "SHORT_ANSWER" } as never },
                    ],
                  },
                },
              ],
            },
          ],
        },
      ],
    };

    const payload = toApiTrialAnswers(paper, {
      "q-sc": { optionId: "o1" },
      "q-mc": { optionIds: ["o1", "o3"] },
      "q-sa": { text: "  42  " },
      "q-cl": { blanks: { b1: "", b2: "  Ha Noi  " } },
      "q-tf": { statements: { s1: true, s2: false } },
      "q-mt": { pairs: { l1: "r2", l2: null } },
      "q-es": { html: "<p>Xin chao</p>", wordCount: 2 },
      "q-c1": { optionId: null },
      "q-c2": { text: "abc" },
    });

    expect(payload).toEqual({
      "q-sc": { type: "SINGLE_CHOICE", optionId: "o1" },
      "q-mc": { type: "MULTIPLE_CHOICE", optionIds: ["o1", "o3"] },
      "q-sa": { type: "SHORT_ANSWER", text: "42" },
      "q-cl": { type: "CLOZE", blanks: { b2: "Ha Noi" } },
      "q-tf": { type: "TRUE_FALSE", statements: { s1: true, s2: false } },
      "q-mt": { type: "MATCHING", pairs: { l1: "r2" } },
      "q-es": { type: "ESSAY", html: "<p>Xin chao</p>", wordCount: 2 },
      "q-c2": { type: "SHORT_ANSWER", text: "abc" },
    });

    const setValue = buildSetAnswerValue(
      [
        { questionId: "q-c1", number: 8, type: "SINGLE_CHOICE" },
        { questionId: "q-c2", number: 9, type: "SHORT_ANSWER" },
      ],
      { "q-c2": { text: "abc" } },
    );
    expect(setValue).toEqual({ children: { "q-c2": { text: "abc" } } });
  });

  it("sliceTrialPartSegments chia chùm qua ranh giới phân đoạn khi firstNumber > 1", () => {
    const part: PreviewTrialPart = {
      partId: "part-en",
      firstNumber: 10,
      displaySegments: [
        { code: "SEG_2", name: "Part II", fromPosition: 2, toPosition: 3 },
        { code: "SEG_1", name: "Part I", fromPosition: 1, toPosition: 1 },
      ],
      blocks: [
        {
          blockId: "blk-set",
          items: [
            {
              kind: "SET",
              set: {
                questionId: "set-1",
                children: [
                  { questionId: "c1", number: 10, type: "SINGLE_CHOICE" },
                  { questionId: "c2", number: 11, type: "SINGLE_CHOICE" },
                  { questionId: "c3", number: 12, type: "SINGLE_CHOICE" },
                ],
              },
            },
          ],
        },
      ],
    };

    const slices = sliceTrialPartSegments(part)!;
    expect(slices).toHaveLength(2);
    expect(slices[0]!.segment.code).toBe("SEG_1");
    expect(slices[0]!.blocks[0]!.items![0]!.set!.children!.map((c) => c.questionId)).toEqual(["c1"]);
    expect(slices[1]!.segment.code).toBe("SEG_2");
    expect(slices[1]!.blocks[0]!.items![0]!.set!.children!.map((c) => c.questionId)).toEqual(["c2", "c3"]);
    expect(slices[1]!.blocks[0]!.firstNumber).toBe(11);
    expect(slices[1]!.blocks[0]!.lastNumber).toBe(12);
  });

  it("computePartScoreSummaries & computeObjectiveTotals loại tự luận bỏ trống và không hiện 0/25 cho phần chỉ có tự luận", () => {
    const paper: PreviewTrialPaperResponse = {
      examId: "ex-bca",
      previewId: "pv-bca",
      parts: [
        {
          partId: "p1",
          name: "Phần 1 · Trắc nghiệm",
          order: 1,
          points: 10,
          blocks: [
            {
              items: [
                { kind: "QUESTION", question: { questionId: "q-obj", number: 1, type: "SINGLE_CHOICE", points: 10 } },
              ],
            },
          ],
        },
        {
          partId: "p3",
          name: "Phần 3 · Tự luận",
          order: 3,
          points: 25,
          blocks: [
            {
              items: [
                { kind: "QUESTION", question: { questionId: "q-es-1", number: 2, type: "ESSAY", points: 15 } },
                { kind: "QUESTION", question: { questionId: "q-es-2", number: 3, type: "ESSAY", points: 10 } },
              ],
            },
          ],
        },
      ],
    };

    const grade: PreviewGradeResponse = {
      examId: "ex-bca",
      previewId: "pv-bca",
      hasPendingManualGrading: true,
      items: [
        { questionId: "q-obj", number: 1, type: "SINGLE_CHOICE", outcome: "CORRECT", earnedPoints: 8, maxPoints: 10, pendingReview: false },
        { questionId: "q-es-1", number: 2, type: "ESSAY", outcome: "PENDING_REVIEW", earnedPoints: 0, maxPoints: 15, pendingReview: true },
        { questionId: "q-es-2", number: 3, type: "ESSAY", outcome: "BLANK", earnedPoints: 0, maxPoints: 10, pendingReview: false },
      ],
    };

    expect(computeObjectiveTotals(grade)).toEqual({
      earnedPoints: 8,
      maxPoints: 10,
      gradedCount: 1,
      pendingCount: 1,
    });

    expect(computePartScoreSummaries(paper, grade)).toEqual([
      {
        partId: "p1",
        partName: "Phần 1 · Trắc nghiệm",
        objectiveCount: 1,
        earnedPoints: 8,
        maxPoints: 10,
        hasPending: false,
        questionCount: 1,
      },
      {
        partId: "p3",
        partName: "Phần 3 · Tự luận",
        objectiveCount: 0,
        earnedPoints: 0,
        maxPoints: 0,
        hasPending: true,
        questionCount: 2,
      },
    ]);
  });

  it("trialPartKey & extractClozeResultEntries xử lý thiếu partId và blanks có null/thưa", () => {
    expect(trialPartKey({ name: "P1" }, 0)).toBe("part-0");
    expect(trialPartKey({ partId: "abc", name: "P1" }, 0)).toBe("abc");

    expect(
      extractClozeResultEntries(
        {
          questionId: "q1",
          type: "CLOZE",
          body: { type: "CLOZE", blanks: [{ id: "b1" }, { id: "b2" }] } as never,
        },
        { b1: null, b2: " 42 " },
      ),
    ).toEqual([{ index: 2, value: "42" }]);

    expect(extractClozeResultEntries(undefined, { b1: "", b2: "Paris" })).toEqual([
      { index: 2, value: "Paris" },
    ]);
  });
});
