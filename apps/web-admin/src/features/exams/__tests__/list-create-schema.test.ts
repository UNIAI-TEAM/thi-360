import { describe, expect, it } from "vitest";
import reference from "@/features/content/mock/reference.json";
import type { ContentReference } from "@/features/content/types";
import {
  combinationsForKind,
  createExamSchema,
  type CreateExamFormValues,
  type CreateExamSchemaMessages,
  initialParts,
  partsEdited,
  summarizeParts,
  toCreateExamRequest,
} from "../create-exam-schema";
import { MOCK_EXAM_TEMPLATES } from "../mock/store";
import type { ExamFormat, ExamKind } from "../types";

const MESSAGES: CreateExamSchemaMessages = {
  nameRequired: "nameRequired",
  nameMax: "nameMax",
  codeRequired: "codeRequired",
  codeInvalid: "codeInvalid",
  codeMax: "codeMax",
  codeTaken: "codeTaken",
  subjectRequired: "subjectRequired",
  descriptionMax: "descriptionMax",
  partNameRequired: "partNameRequired",
  partNameMax: "partNameMax",
  partSubjectRequired: "partSubjectRequired",
  questionCountPositive: "questionCountPositive",
  durationPositive: "durationPositive",
  noOptions: "noOptions",
  subjectCountPositive: "subjectCountPositive",
  allocationTotal: (actual, expected) => `allocationTotal ${actual}/${expected}`,
  partsRequired: "partsRequired",
};

const COMBINATIONS = (reference as ContentReference).combinations;

function parts(kind: ExamKind, format: ExamFormat, singleScope = "") {
  return initialParts({
    templates: MOCK_EXAM_TEMPLATES,
    kind,
    format,
    singleScope,
    combinations: combinationsForKind(COMBINATIONS, kind),
    scopeName: (scope) => scope,
  });
}

function values(overrides: Partial<CreateExamFormValues> = {}): CreateExamFormValues {
  return {
    name: "HSA FULL thu",
    code: "hsa-f-003",
    kind: "HSA",
    format: "FULL",
    singleSubjectCode: "",
    description: "",
    parts: parts("HSA", "FULL"),
    ...overrides,
  };
}

/** Lỗi đầu tiên theo đường dẫn trường. */
function errors(input: CreateExamFormValues, taken: ReadonlySet<string> = new Set()) {
  const result = createExamSchema(MESSAGES, taken).safeParse(input);
  if (result.success) return {};
  const map: Record<string, string> = {};
  for (const issue of result.error.issues) map[issue.path.join(".")] ??= issue.message;
  return map;
}

describe("mẫu cấu trúc khởi tạo", () => {
  it("HSA FULL: 3 phần 150 câu 195 phút, phần 3 có Tiếng Anh và tổ hợp 3 môn 17–17–16", () => {
    const hsa = parts("HSA", "FULL");
    expect(hsa.map((part) => [part.questionCount, part.durationMinutes, part.mode, part.subjectCode])).toEqual([
      [50, 75, "FIXED", "MATH"],
      [50, 60, "FIXED", "LITERATURE"],
      [50, 60, "CHOICE", ""],
    ]);
    expect(hsa[2]!.englishOption).toBe(true);
    expect(hsa[2]!.combinations.map((item) => [item.code, item.subjectCounts])).toEqual([
      ["KH-1", [17, 17, 16]],
      ["KH-2", [17, 17, 16]],
      ["KH-3", [17, 17, 16]],
    ]);
    expect(summarizeParts(hsa)).toEqual({ partCount: 3, totalQuestions: 150, totalMinutes: 195, pathCount: 4 });
  });

  it("QDA FULL: Toán 50/80, Văn 50/55, phần 3 50/60 với tổ hợp 2 môn 25–25", () => {
    const qda = parts("QDA", "FULL");
    expect(qda.map((part) => [part.questionCount, part.durationMinutes])).toEqual([
      [50, 80],
      [50, 55],
      [50, 60],
    ]);
    expect(qda[2]!.combinations.map((item) => [item.code, item.subjectCounts])).toEqual([
      ["KH-4", [25, 25]],
      ["KH-5", [25, 25]],
      ["KH-6", [25, 25]],
    ]);
  });

  it("bài lẻ: Toán theo phần Toán của mẫu FULL; Khoa học là phần tự chọn không có Tiếng Anh", () => {
    expect(parts("QDA", "SINGLE", "MATH")).toMatchObject([{ mode: "FIXED", subjectCode: "MATH", questionCount: 50, durationMinutes: 80 }]);
    const science = parts("HSA", "SINGLE", "SCIENCE");
    expect(science).toMatchObject([{ mode: "CHOICE", englishOption: false, questionCount: 50, durationMinutes: 60 }]);
    expect(summarizeParts(science).pathCount).toBe(3);
  });

  it("biết cấu trúc đã bị sửa so với mẫu", () => {
    const base = parts("HSA", "FULL");
    expect(partsEdited(base.map((part) => ({ ...part, key: "khac" })), base)).toBe(false);
    expect(partsEdited([{ ...base[0]!, questionCount: 40 }, ...base.slice(1)], base)).toBe(true);
  });
});

describe("kiểm tra trang Tạo bài thi", () => {
  it("mẫu hợp lệ thì không có lỗi; mã đưa về chữ hoa khi gửi", () => {
    expect(errors(values())).toEqual({});
    const request = toCreateExamRequest(values(), MOCK_EXAM_TEMPLATES);
    expect(request).toMatchObject({ code: "HSA-F-003", templateCode: "HSA_FULL", singleSubjectCode: null, description: null });
    expect(request.parts?.[2]).toMatchObject({ mode: "CHOICE", subjectCode: null, englishOption: true });
  });

  it("mã trùng (đã biết từ máy chủ) bị báo, không phân biệt hoa thường", () => {
    expect(errors(values({ code: "hsa-f-001" }), new Set(["HSA-F-001"]))).toEqual({ code: "codeTaken" });
    expect(errors(values({ code: "HSA F 1" }))).toEqual({ code: "codeInvalid" });
  });

  it("bài lẻ phải chọn phần/môn", () => {
    expect(errors(values({ format: "SINGLE", singleSubjectCode: "", parts: parts("HSA", "SINGLE", "MATH") }))).toEqual({
      singleSubjectCode: "subjectRequired",
    });
  });

  it("số câu và thời lượng của phần phải là số nguyên dương; phần cố định phải có môn", () => {
    const base = parts("HSA", "FULL");
    const broken = [{ ...base[0]!, questionCount: 0, durationMinutes: 0, subjectCode: "", name: " " }, ...base.slice(1)];
    expect(errors(values({ parts: broken }))).toEqual({
      "parts.0.name": "partNameRequired",
      "parts.0.questionCount": "questionCountPositive",
      "parts.0.durationMinutes": "durationPositive",
      "parts.0.subjectCode": "partSubjectRequired",
    });
    expect(errors(values({ parts: [] }))).toEqual({ parts: "partsRequired" });
  });

  it("tổng số câu các môn của tổ hợp phải bằng số câu của phần (51/50 bị báo)", () => {
    const base = parts("HSA", "FULL");
    const choice = base[2]!;
    const over = { ...choice, combinations: choice.combinations.map((item, index) => (index === 2 ? { ...item, subjectCounts: [17, 17, 17] } : item)) };
    expect(errors(values({ parts: [base[0]!, base[1]!, over] }))).toEqual({
      "parts.2.combinations.2.subjectCounts": "allocationTotal 51/50",
    });
    const zero = { ...choice, combinations: [{ code: "KH-1", subjectCounts: [25, 25, 0] }] };
    expect(errors(values({ parts: [base[0]!, base[1]!, zero] }))).toEqual({
      "parts.2.combinations.0.subjectCounts": "subjectCountPositive",
    });
  });

  it("phần tự chọn phải có ít nhất một đường lựa chọn", () => {
    const base = parts("HSA", "FULL");
    const empty = { ...base[2]!, englishOption: false, combinations: [] };
    expect(errors(values({ parts: [base[0]!, base[1]!, empty] }))).toEqual({ "parts.2.combinations": "noOptions" });
    expect(summarizeParts([base[0]!, base[1]!, empty]).pathCount).toBe(0);
  });
});
