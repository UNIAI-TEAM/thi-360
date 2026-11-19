import type {
  PreviewBlock,
  PreviewDisplaySegment,
  PreviewItem,
  PreviewPaper,
  PreviewPart,
  PreviewQuestion,
} from "../review-types";

/** Câu của đề xem trước, số câu liền theo tham số; `over` ghi đè từng trường. */
export function question(number: number, over: Partial<PreviewQuestion> = {}): PreviewQuestion {
  return {
    number,
    questionId: `q-${number}`,
    code: `Q${number}`,
    version: 1,
    type: "SINGLE_CHOICE",
    simulated: false,
    content: `<p>Stem ${number}</p>`,
    gradeCode: "GRADE_12",
    difficultyCode: "APPLICATION",
    knowledge: null,
    points: 1,
    options: [
      { id: "a", content: "<p>Option A text</p>", correct: true },
      { id: "b", content: "<p>Option B text</p>", correct: false },
    ],
    answer: null,
    solution: null,
    rubric: null,
    body: null,
    originalNumber: null,
    optionOrder: null,
    originalOptionOrder: null,
    assignedTopicQuotaIds: [],
    ...over,
  };
}

export const single = (value: PreviewQuestion): PreviewItem => ({ kind: "QUESTION", question: value, originalOrder: null });

export function set(
  id: string,
  children: PreviewQuestion[],
  over: Partial<Extract<PreviewItem, { kind: "SET" }>> = {},
): PreviewItem {
  return {
    kind: "SET",
    setId: id,
    code: `SET-${id}`,
    version: 1,
    simulated: false,
    passage: `<p>Passage of ${id}</p>`,
    children,
    originalOrder: null,
    ...over,
  };
}

export function block(over: Partial<PreviewBlock> & { items: PreviewItem[] }): PreviewBlock {
  return {
    blockId: "b1",
    blockName: "Group",
    subjectCode: "MATHEMATICS",
    questionType: "SINGLE_CHOICE",
    manualGrading: false,
    pointsEach: 1,
    firstNumber: 1,
    lastNumber: over.items.length,
    questionCount: over.items.length,
    setCount: null,
    setSize: null,
    requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] },
    allocation: [],
    ...over,
  };
}

export function partOf(over: Partial<PreviewPart> & { blocks: PreviewBlock[] }): PreviewPart {
  return {
    partId: "p1",
    name: "Part 1",
    order: 1,
    durationMinutes: 30,
    firstNumber: 1,
    lastNumber: 1,
    points: 1,
    maxPoints: null,
    ...over,
  };
}

export function paperOf(parts: PreviewPart[]): PreviewPaper {
  return {
    totalQuestions: Math.max(...parts.map((part) => part.lastNumber)),
    totalPoints: 10,
    totalMinutes: 60,
    sharedDuration: false,
    hasManualGrading: false,
    subjects: [],
    checks: [],
    knowledgeStats: [],
    parts,
  };
}

export const segment = (
  code: string,
  name: string,
  from: number,
  to: number,
  commandCount = 1,
  maxPoints = 1,
): PreviewDisplaySegment => ({ code, name, fromPosition: from, toPosition: to, commandCount, maxPoints });
