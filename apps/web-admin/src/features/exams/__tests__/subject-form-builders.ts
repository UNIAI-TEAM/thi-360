import { mapParts } from "../api-mapping";
import type { ExamDetail, ExamPart, ExamSection, QuestionBlock } from "../types";
import standardTemplates from "./fixtures/exam-templates-dot3.json";
import reviewTemplates from "./fixtures/exam-templates-dot3-review.json";

/** Hàm dựng dữ liệu cho các test `subject-form-*`: bài dựng trong test, không dùng chung DB (plan CR-1, dữ liệu test). */

export function block(id: string, over: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id,
    name: id,
    questionType: "SINGLE_CHOICE",
    questionCount: 10,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    ...over,
  };
}

export const setBlock = (id: string, setCount: number, setSize: number, over: Partial<QuestionBlock> = {}): QuestionBlock =>
  block(id, { questionType: "QUESTION_SET", setCount, setSize, questionCount: setCount * setSize, ...over });

export function section(id: string, subjectCode: string, blocks: QuestionBlock[], over: Partial<ExamSection> = {}): ExamSection {
  return {
    id,
    subjectCode,
    questionCount: blocks.reduce((total, item) => total + (item.questionCount ?? 0), 0),
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks,
    ...over,
  };
}

export function part(id: string, sections: ExamSection[], over: Partial<ExamPart> = {}): ExamPart {
  const total = sections.reduce((sum, item) => sum + (item.questionCount ?? 0), 0);
  return {
    id,
    order: 1,
    name: `Part ${id}`,
    durationMinutes: 60,
    questionCount: total,
    questionCountOnPaper: total,
    questionCountRequired: total,
    participation: { mode: "ALL", choose: null },
    mode: "FIXED",
    sections,
    options: [],
    ...over,
  };
}

export const standardExam = (kind: string): Pick<ExamDetail, "kind" | "buildMode"> => ({ kind, buildMode: "STANDARD" });
export const reviewExam = (kind: string): Pick<ExamDetail, "kind" | "buildMode"> => ({ kind, buildMode: "REVIEW" });

/** Môn Khoa học QDA/HSA đúng như mẫu: 14 câu đơn, 1 chùm × 3, 1 câu điền (Lý, Hóa, Sinh). */
export function scienceSection(id: string, subjectCode = "LY"): ExamSection {
  return section(id, subjectCode, [block(`${id}-single`, { questionCount: 14 }), setBlock(`${id}-set`, 1, 3), block(`${id}-fill`, { questionType: "SHORT_ANSWER", questionCount: 1 })]);
}

export interface FixtureTemplate {
  code: string;
  kindCode: string;
  scope: string;
  buildMode: string;
  partCode: string | null;
  subjectCode: string | null;
  structureYear: number | null;
  parts: ExamPart[];
}

type WireTemplate = (typeof standardTemplates)["templates"][number];

const toTemplate = (template: WireTemplate | (typeof reviewTemplates)["templates"][number]): FixtureTemplate => {
  const wire = template as unknown as {
    code: string;
    kindCode: string;
    scope: string;
    buildMode: string;
    partCode?: string | null;
    subjectCode?: string | null;
    structureYear?: number | null;
    structure: { parts: Parameters<typeof mapParts>[0] };
  };
  return {
    code: wire.code,
    kindCode: wire.kindCode,
    scope: wire.scope,
    buildMode: wire.buildMode,
    partCode: wire.partCode ?? null,
    subjectCode: wire.subjectCode ?? null,
    structureYear: wire.structureYear ?? null,
    parts: mapParts(wire.structure.parts),
  };
};

/** Mẫu chuẩn đang hoạt động đã xuất (fixture B8), không gồm mẫu bài ôn. */
export const standardFixtureTemplates = (): FixtureTemplate[] => standardTemplates.templates.map(toTemplate);

/** Mẫu bài ôn đã xuất, chỉ dùng cho ca AC-4/AC-46. */
export const reviewFixtureTemplates = (): FixtureTemplate[] => reviewTemplates.templates.map(toTemplate);

export const fixtureSource = standardTemplates.source;
