import { describe, expect, it } from "vitest";
import {
  asAnswerFormat,
  asQuestionType,
  asScoringRule,
  asTrueFalseMethod,
  filterAudioAttachments,
  isAudioMime,
  parseRawQuestionBody,
  readRawQuestionBody,
} from "../index";

describe("question wire-parsers (@thi360/ui/question)", () => {
  it("validates enum helpers (asQuestionType, asAnswerFormat, asScoringRule, asTrueFalseMethod)", () => {
    expect(asQuestionType("SINGLE_CHOICE")).toBe("SINGLE_CHOICE");
    expect(asQuestionType("MCQ_SINGLE")).toBe("SINGLE_CHOICE");
    expect(asQuestionType("MCQ_MULTI")).toBe("MULTIPLE_CHOICE");
    expect(asQuestionType("QUESTION_SET")).toBe("QUESTION_SET");
    expect(asQuestionType("UNKNOWN")).toBeUndefined();
    expect(asQuestionType(null)).toBeUndefined();

    expect(asAnswerFormat("INTEGER")).toBe("INTEGER");
    expect(asAnswerFormat("DECIMAL")).toBe("DECIMAL");
    expect(asAnswerFormat("FRACTION")).toBe("FRACTION");
    expect(asAnswerFormat("TEXT")).toBe("TEXT");
    expect(asAnswerFormat("CHEM_FORMULA")).toBe("CHEM_FORMULA");
    expect(asAnswerFormat("CHEM_EQUATION")).toBe("CHEM_EQUATION");
    expect(asAnswerFormat("INVALID")).toBeNull();
    expect(asAnswerFormat(undefined)).toBeNull();

    expect(asScoringRule("ALL_OR_NOTHING")).toBe("ALL_OR_NOTHING");
    expect(asScoringRule("PARTIAL")).toBe("PARTIAL");
    expect(asScoringRule("OTHER")).toBeUndefined();

    expect(asTrueFalseMethod("PER_STATEMENT")).toBe("PER_STATEMENT");
    expect(asTrueFalseMethod("COUNT_TABLE")).toBe("COUNT_TABLE");
    expect(asTrueFalseMethod("OTHER")).toBeUndefined();

    expect(readRawQuestionBody(null)).toBeUndefined();
    expect(readRawQuestionBody(["not-an-object"])).toBeUndefined();
    expect(readRawQuestionBody({ type: "ESSAY", wordLimit: 150 })).toEqual({
      type: "ESSAY",
      wordLimit: 150,
    });
  });

  it("parses all 8 question types in sanitized student/trial mode (includeAnswers = false) and rewrites HTML", () => {
    const rewrite = (html: string | undefined | null) =>
      (html ?? "").replaceAll("/admin/", "/attempt/");

    expect(
      parseRawQuestionBody(
        {
          type: "SINGLE_CHOICE",
          shuffleOptions: false,
          options: [
            { id: "o1", content: '<p><img src="/admin/a.png"></p>', correct: true },
            { id: "o2", content: "<p>B</p>", correct: false },
          ],
        },
        { rewriteHtml: rewrite },
      ),
    ).toEqual({
      type: "SINGLE_CHOICE",
      options: [
        { id: "o1", content: '<p><img src="/attempt/a.png"></p>' },
        { id: "o2", content: "<p>B</p>" },
      ],
    });

    expect(
      parseRawQuestionBody({
        type: "MULTIPLE_CHOICE",
        scoringRule: "PARTIAL",
        options: [{ id: "m1", content: "Opt 1", correct: true }],
      }),
    ).toEqual({
      type: "MULTIPLE_CHOICE",
      scoringRule: "PARTIAL",
      options: [{ id: "m1", content: "Opt 1" }],
    });

    expect(
      parseRawQuestionBody({
        type: "SHORT_ANSWER",
        answers: ["42", "42.0"],
        inputHint: "Enter integer",
        answerFormat: "INTEGER",
      }),
    ).toEqual({
      type: "SHORT_ANSWER",
      answers: [],
      inputHint: "Enter integer",
      answerFormat: "INTEGER",
    });

    // Specifically pins CLOZE blankNumberStart preservation and stripping of answers
    expect(
      parseRawQuestionBody(
        {
          type: "CLOZE",
          passage: '<p>Fill [[b1]] and [[b2]] <img src="/admin/p.png"></p>',
          blankNumberStart: 12,
          blanks: [
            { id: "b1", answers: ["Hanoi"], points: 0.5 },
            { id: "b2", answers: ["Hue"], points: 0.5 },
          ],
        },
        { rewriteHtml: rewrite },
      ),
    ).toEqual({
      type: "CLOZE",
      passage: '<p>Fill [[b1]] and [[b2]] <img src="/attempt/p.png"></p>',
      blankNumberStart: 12,
      blanks: [
        { id: "b1", answers: [] },
        { id: "b2", answers: [] },
      ],
    });

    expect(
      parseRawQuestionBody({
        type: "TRUE_FALSE",
        scoringMethod: "COUNT_TABLE",
        scoreTable: [0.1, 0.25, 0.5, 1],
        statements: [{ id: "s1", content: "Statement 1", answer: true, points: 0.25 }],
      }),
    ).toEqual({
      type: "TRUE_FALSE",
      statements: [{ id: "s1", content: "Statement 1" }],
    });

    expect(
      parseRawQuestionBody({
        type: "MATCHING",
        left: [{ id: "l1", content: "Left 1" }],
        right: [{ id: "r1", content: "Right 1" }],
        pairs: [{ leftId: "l1", rightId: "r1", points: 1 }],
        reuseRight: true,
      }),
    ).toEqual({
      type: "MATCHING",
      left: [{ id: "l1", content: "Left 1" }],
      right: [{ id: "r1", content: "Right 1" }],
      reuseRight: true,
    });

    expect(
      parseRawQuestionBody({
        type: "ESSAY",
        wordLimit: 250,
        gradingGuide: "<p>Secret guide</p>",
        sampleAnswer: "<p>Sample</p>",
        rubric: [{ id: "c1", description: "Clarity", points: 5 }],
      }),
    ).toEqual({
      type: "ESSAY",
      wordLimit: 250,
    });

    expect(
      parseRawQuestionBody(
        {
          type: "QUESTION_SET",
          passage: '<p>Shared <img src="/admin/s.png"></p>',
          blankNumberStart: 21,
        },
        { rewriteHtml: rewrite, childIds: ["c-1", "c-2"] },
      ),
    ).toEqual({
      type: "QUESTION_SET",
      passage: '<p>Shared <img src="/attempt/s.png"></p>',
      blankNumberStart: 21,
      childIds: ["c-1", "c-2"],
    });
  });

  it("retains answer keys, rubrics, scoring tables, and legacy fallbacks when includeAnswers = true", () => {
    expect(
      parseRawQuestionBody(
        { type: "SINGLE_CHOICE", shuffleOptions: false, options: [{ id: "o1", content: "A" }] },
        {
          includeAnswers: true,
          fallbackOptions: [{ id: "o1", content: "A", correct: true }],
        },
      ),
    ).toEqual({
      type: "SINGLE_CHOICE",
      shuffleOptions: false,
      options: [{ id: "o1", content: "A", correct: true }],
    });

    expect(
      parseRawQuestionBody(undefined, {
        questionType: "SHORT_ANSWER",
        includeAnswers: true,
        fallbackAnswer: "H2SO4",
      }),
    ).toEqual({
      type: "SHORT_ANSWER",
      answers: ["H2SO4"],
      inputHint: null,
      answerFormat: null,
    });

    expect(
      parseRawQuestionBody(
        {
          type: "CLOZE",
          passage: "Value [[b1]]",
          blankNumberStart: 5,
          blanks: [{ id: "b1", answers: ["3.14", "3,14"], points: 2 }],
        },
        { includeAnswers: true },
      ),
    ).toEqual({
      type: "CLOZE",
      passage: "Value [[b1]]",
      blankNumberStart: 5,
      blanks: [{ id: "b1", answers: ["3.14", "3,14"], points: 2 }],
    });

    expect(
      parseRawQuestionBody(
        {
          type: "ESSAY",
          gradingGuide: "<p>Guide</p>",
          sampleAnswer: "<p>Sample</p>",
          wordLimit: 200,
        },
        {
          includeAnswers: true,
          fallbackRubric: [{ id: "r1", description: "Structure", points: 3 }],
        },
      ),
    ).toEqual({
      type: "ESSAY",
      gradingGuide: "<p>Guide</p>",
      sampleAnswer: "<p>Sample</p>",
      wordLimit: 200,
      rubric: [{ id: "r1", description: "Structure", points: 3 }],
    });
  });

  it("filters audio attachments across mime, mimeType, mediaType, and kind shapes", () => {
    expect(isAudioMime("audio/mpeg")).toBe(true);
    expect(isAudioMime("  AUDIO/WAV ")).toBe(true);
    expect(isAudioMime("image/png")).toBe(false);
    expect(isAudioMime(null)).toBe(false);

    expect(filterAudioAttachments(null)).toEqual([]);
    expect(filterAudioAttachments(undefined)).toEqual([]);

    const mixed = [
      { id: "1", mime: "audio/mpeg" },
      { id: "2", mime: "application/pdf" },
      { id: "3", mimeType: "AUDIO/OGG" },
      { id: "4", mediaType: "audio/mp4" },
      { id: "5", kind: "audio" },
      { id: "6", kind: "IMAGE", mime: "image/jpeg" },
    ];

    expect(filterAudioAttachments(mixed).map((item) => item.id)).toEqual(["1", "3", "4", "5"]);
  });
});
