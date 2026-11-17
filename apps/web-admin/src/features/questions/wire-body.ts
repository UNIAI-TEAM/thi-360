import type { components } from "@thi360/api-client";
import type { MultipleChoiceRule, TrueFalseMethod } from "@/features/content/types";
import type { ChildQuestionType, SaveChildQuestionRequest } from "./editor-types";
import type { QuestionBody } from "./types";

/**
 * Phần thân câu hỏi trên dây (JSON của `QuestionBody` phía Java, spec 4.3). OpenAPI sinh ra kiểu lỏng và tự mâu thuẫn
 * (`type` vừa là tên lớp vừa là mã dạng), nên đọc và ghi đi qua các kiểu dưới đây, một chỗ duy nhất ép kiểu.
 * Trường thiếu hoặc `null` được điền giá trị mặc định khi đọc; web luôn gửi đủ trường khi ghi.
 */

export type ApiQuestionBody = NonNullable<components["schemas"]["QuestionDetail"]["body"]>;

interface WireChoice {
  id?: string | null;
  content?: string | null;
  correct?: boolean | null;
}

interface WireSide {
  id?: string | null;
  content?: string | null;
}

export interface WireChild {
  id?: string | null;
  type: ChildQuestionType;
  content?: string | null;
  body?: WireBody | null;
  solution?: string | null;
  points?: number | null;
  gradeItemId?: string | null;
  levelItemId?: string | null;
  knowledgeItemIds?: string[] | null;
}

export type WireBody =
  | { type: "SINGLE_CHOICE"; options?: WireChoice[] | null }
  | {
      type: "MULTIPLE_CHOICE";
      options?: WireChoice[] | null;
      scoringRule?: MultipleChoiceRule | null;
    }
  | { type: "SHORT_ANSWER"; answers?: string[] | null; inputHint?: string | null }
  | {
      type: "CLOZE";
      passage?: string | null;
      blanks?: { id?: string | null; answers?: string[] | null; points?: number | null }[] | null;
    }
  | { type: "QUESTION_SET"; passage?: string | null; children?: WireChild[] | null }
  | {
      type: "TRUE_FALSE";
      statements?:
        | { id?: string | null; content?: string | null; answer?: boolean | null; points?: number | null }[]
        | null;
      scoringMethod?: TrueFalseMethod | null;
      scoreTable?: number[] | null;
    }
  | {
      type: "MATCHING";
      left?: WireSide[] | null;
      right?: WireSide[] | null;
      pairs?: { leftId?: string | null; rightId?: string | null; points?: number | null }[] | null;
      reuseRight?: boolean | null;
      shuffleLeft?: boolean | null;
      shuffleRight?: boolean | null;
    }
  | {
      type: "ESSAY";
      gradingGuide?: string | null;
      rubric?: { id?: string | null; description?: string | null; points?: number | null }[] | null;
      sampleAnswer?: string | null;
      wordLimit?: number | null;
    };

export function asWireBody(body: ApiQuestionBody): WireBody {
  return body as unknown as WireBody;
}

export function asApiBody(body: WireBody): ApiQuestionBody {
  return body as unknown as ApiQuestionBody;
}

const choice = (option: WireChoice) => ({
  id: option.id ?? "",
  content: option.content ?? "",
  correct: option.correct ?? false,
});

const side = (item: WireSide) => ({ id: item.id ?? "", content: item.content ?? "" });

/** Phần thân trên dây -> phần thân của web. `childIds` là id các câu con (bộ câu hỏi), lấy từ `children` của chi tiết. */
export function fromWireBody(wire: WireBody, childIds: readonly string[] = []): QuestionBody {
  switch (wire.type) {
    case "SINGLE_CHOICE":
      return { type: "SINGLE_CHOICE", options: (wire.options ?? []).map(choice) };
    case "MULTIPLE_CHOICE":
      return {
        type: "MULTIPLE_CHOICE",
        options: (wire.options ?? []).map(choice),
        scoringRule: wire.scoringRule ?? "ALL_OR_NOTHING",
      };
    case "SHORT_ANSWER":
      return { type: "SHORT_ANSWER", answers: wire.answers ?? [], inputHint: wire.inputHint ?? null };
    case "CLOZE":
      return {
        type: "CLOZE",
        passage: wire.passage ?? "",
        blanks: (wire.blanks ?? []).map((blank) => ({
          id: blank.id ?? "",
          answers: blank.answers ?? [],
          points: blank.points ?? 1,
        })),
      };
    case "QUESTION_SET":
      return {
        type: "QUESTION_SET",
        passage: wire.passage ?? "",
        childIds: childIds.length > 0 ? [...childIds] : (wire.children ?? []).flatMap((child) => (child.id ? [child.id] : [])),
      };
    case "TRUE_FALSE":
      return {
        type: "TRUE_FALSE",
        statements: (wire.statements ?? []).map((statement) => ({
          id: statement.id ?? "",
          content: statement.content ?? "",
          answer: statement.answer ?? false,
          points: statement.points ?? null,
        })),
        scoringMethod: wire.scoringMethod ?? "PER_STATEMENT",
        scoreTable: wire.scoreTable ?? [],
      };
    case "MATCHING":
      return {
        type: "MATCHING",
        left: (wire.left ?? []).map(side),
        right: (wire.right ?? []).map(side),
        pairs: (wire.pairs ?? []).map((pair) => ({
          leftId: pair.leftId ?? "",
          rightId: pair.rightId ?? "",
          points: pair.points ?? 0,
        })),
        reuseRight: wire.reuseRight ?? false,
        shuffleLeft: wire.shuffleLeft ?? false,
        shuffleRight: wire.shuffleRight ?? false,
      };
    case "ESSAY":
      return {
        type: "ESSAY",
        gradingGuide: wire.gradingGuide ?? "",
        rubric: (wire.rubric ?? []).map((criterion) => ({
          id: criterion.id ?? "",
          description: criterion.description ?? "",
          points: criterion.points ?? 0,
        })),
        sampleAnswer: wire.sampleAnswer ?? null,
        wordLimit: wire.wordLimit ?? null,
      };
  }
}

/** Mã -> id giá trị danh mục cho phân loại riêng của câu con (`null` là kế thừa của bộ). */
export interface ChildClassificationIds {
  gradeItemId: (code: string) => string | null;
  levelItemId: (code: string) => string | null;
}

function toWireChild(child: SaveChildQuestionRequest, ids: ChildClassificationIds): WireChild {
  return {
    id: child.id,
    type: child.type,
    content: child.content,
    body: toWireBody(child.body, [], ids),
    solution: child.solution,
    points: child.points,
    gradeItemId: child.gradeCode ? ids.gradeItemId(child.gradeCode) : null,
    levelItemId: child.difficultyCode ? ids.levelItemId(child.difficultyCode) : null,
    knowledgeItemIds: child.knowledgeIds,
  };
}

/** Phần thân của web -> trên dây. Bộ câu hỏi mang câu con trong `body.children`, không có `childIds`. */
export function toWireBody(
  body: QuestionBody,
  children: readonly SaveChildQuestionRequest[],
  ids: ChildClassificationIds,
): WireBody {
  if (body.type === "QUESTION_SET") {
    return { type: "QUESTION_SET", passage: body.passage, children: children.map((child) => toWireChild(child, ids)) };
  }
  return body;
}
