import type { CombinationOption } from "@/features/content/types";
import { draftId } from "./components/exam-structure-draft";
import { countOf, emptyMatrix, splitEvenly, sum } from "./matrix";
import type {
  CreateExamPartInput,
  ExamPart,
  ExamPartOption,
  ExamSection,
  ExamTemplateBlock,
  ExamTemplateSection,
  QuestionBlock,
} from "./types";

/** Cấu trúc khởi tạo của trang Tạo bài thi (`CreateExamPartInput[]`) thành `ExamPart[]`; dùng cho dữ liệu mẫu và khi lưu cấu trúc đã sửa sau khi tạo. */

const ENGLISH = "ENGLISH";

/** Môn mới chưa có chỉ tiêu cấp môn (D26: kỳ thi ngoài HSA giữ môn không có chỉ tiêu). */
export function newSection(subjectCode: string, questionCount: number | null): ExamSection {
  return {
    id: draftId("sec"),
    subjectCode,
    questionCount,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    groupOrder: "SHUFFLE",
    choiceGroup: null,
    blocks: [],
  };
}

function emptySections(subjectCodes: readonly string[], questionCount: number): ExamSection[] {
  const counts = splitEvenly(questionCount, subjectCodes.length);
  return subjectCodes.map((subjectCode, index) => newSection(subjectCode, counts[index] ?? 0));
}

/**
 * Nhóm lấy câu từ nhóm của mẫu (B9): dạng trả lời, số câu, điểm mỗi câu, câu chùm, mã nhóm của mẫu, năng lực (lọc
 * nguồn theo năng lực) và quy tắc chấm mong đợi. Mẫu không ghi chỉ tiêu riêng (`matrix` bỏ trống) thì nhóm có ma trận
 * trống để soạn ở Cấu trúc và ma trận; `matrix = null` là nhóm không có chỉ tiêu riêng.
 */
function blockFromTemplate(block: ExamTemplateBlock): QuestionBlock {
  return {
    id: draftId("blk"),
    name: block.name,
    questionType: block.questionType,
    questionCount: block.questionCount,
    setCount: block.setCount ?? null,
    setSize: block.setSize ?? null,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
    knowledgeScope:
      block.knowledgeScope !== undefined
        ? structuredClone(block.knowledgeScope)
        : block.competencyCode
          ? { nodeIds: [], match: "ANY", competencyCodes: [block.competencyCode] }
          : null,
    tagCodes: [],
    matrix: block.matrix !== undefined ? structuredClone(block.matrix) : emptyMatrix(),
    taskTypeCode: block.taskTypeCode ?? null,
    childAnswerType: block.childAnswerType ?? null,
    templateGroupCode: block.templateGroupCode ?? null,
    answerTypeLocked: block.answerTypeLocked ?? false,
    shuffle: block.shuffle ?? "SHUFFLE",
    ...(block.expectedScoring ? { expectedScoring: structuredClone(block.expectedScoring) } : {}),
  };
}

export function blocksFromTemplate(blocks: readonly ExamTemplateBlock[] | undefined): QuestionBlock[] {
  return (blocks ?? []).map(blockFromTemplate);
}

/** Môn từ môn của mẫu: nhóm khởi tạo sẵn, phạm vi MAPID và chỉ tiêu cấp môn (bỏ trống là `null`). */
export function sectionFromTemplate(section: ExamTemplateSection): ExamSection {
  return {
    ...newSection(section.subjectCode, section.questionCount),
    knowledgeScope: structuredClone(section.knowledgeScope ?? null),
    answerQuotas: structuredClone(section.answerQuotas ?? null),
    matrix: structuredClone(section.matrix ?? null),
    groupOrder: section.groupOrder ?? "SHUFFLE",
    choiceGroup: section.choiceGroup ?? null,
    blocks: blocksFromTemplate(section.blocks),
  };
}

/**
 * Một lựa chọn của phần tự chọn: một môn (Tiếng Anh) hoặc một tổ hợp, số câu chia đều theo phần dư lớn nhất. Khối
 * gợi ý của mẫu (tổ hợp một môn CA1–CA4) gắn vào môn đầu tiên; `expertConfirmed` giữ theo mẫu.
 */
function buildPartOption(
  combinations: readonly CombinationOption[],
  code: string,
  questionCount: number,
  extra: { blocks?: readonly ExamTemplateBlock[]; expertConfirmed?: boolean } = {},
): ExamPartOption {
  const combination = combinations.find((item) => item.code === code);
  const sections = emptySections(combination ? combination.subjectCodes : [code], questionCount).map((section, index) =>
    index === 0 ? { ...section, blocks: blocksFromTemplate(extra.blocks) } : section,
  );
  return {
    id: draftId("opt"),
    kind: combination ? "COMBINATION" : "SUBJECT",
    code,
    sections,
    chooseSubjects: null,
    countsByPick: null,
    ...(extra.expertConfirmed !== undefined ? { expertConfirmed: extra.expertConfirmed } : {}),
  };
}

/** Phương án `SUBJECT_CHOICE` (HSA Khoa học): các môn khai nhóm và nguồn một lần, số câu chia theo `choiceGroup` (B10, D25). */
function subjectChoiceOption(choice: NonNullable<CreateExamPartInput["subjectChoice"]>): ExamPartOption {
  return {
    id: draftId("opt"),
    kind: "SUBJECT_CHOICE",
    code: choice.code,
    name: choice.name ?? null,
    sections: choice.sections.map(sectionFromTemplate),
    chooseSubjects: choice.chooseSubjects,
    countsByPick: [...choice.countsByPick],
  };
}

/**
 * Cấu trúc khởi tạo người tạo đã chỉnh ở trang Tạo bài thi: phần cố định một môn (kèm khối gợi ý) hoặc nhiều môn;
 * phần tự chọn gồm phần chung (nếu có) cộng một lựa chọn: Tiếng Anh, tổ hợp, nhánh nội dung. Môn của nhánh là môn của
 * phần chung, không có thì môn của bài.
 */
export function partsFromInput(
  inputs: readonly CreateExamPartInput[],
  combinations: readonly CombinationOption[],
  examSubjectCode: string | null = null,
): ExamPart[] {
  return inputs.map((input, index) => {
    const choice = input.mode === "CHOICE";
    const common: ExamSection[] = (input.sections ?? []).map(sectionFromTemplate);
    const target = input.questionCount - sum(common.map(countOf));
    const branchSubject = common[0]?.subjectCode ?? examSubjectCode ?? "";
    const options: ExamPartOption[] = choice
      ? [
          ...(input.englishOption ? [buildPartOption(combinations, ENGLISH, target)] : []),
          ...input.combinations.map((item) => {
            const option = buildPartOption(combinations, item.code, target, item);
            return {
              ...option,
              sections: option.sections.map((section, sectionIndex) => ({
                ...section,
                questionCount: item.subjectCounts[sectionIndex] ?? section.questionCount,
              })),
            };
          }),
          ...(input.subjectChoice ? [subjectChoiceOption(input.subjectChoice)] : []),
          ...(input.branches ?? []).map(
            (branch): ExamPartOption => ({
              id: draftId("opt"),
              kind: "BRANCH",
              code: branch.code,
              name: branch.name.trim(),
              sections: [
                {
                  ...newSection(branchSubject, branch.questionCount),
                  blocks: blocksFromTemplate(branch.blocks),
                },
              ],
              chooseSubjects: null,
              countsByPick: null,
            }),
          ),
        ]
      : [];
    const singleSubject: ExamSection = {
      ...newSection(input.subjectCode ?? "", input.questionCount),
      blocks: blocksFromTemplate(input.blocks),
    };
    return {
      id: draftId("part"),
      order: index + 1,
      name: input.name.trim(),
      durationMinutes: input.durationMinutes,
      questionCount: input.questionCount,
      questionCountOnPaper: input.questionCount,
      questionCountRequired: input.questionCount,
      participation: choice ? { mode: "CHOICE", choose: 1 } : { mode: "ALL", choose: null },
      maxPoints: input.points ?? null,
      mode: input.mode,
      sections: choice || common.length > 0 ? common : [singleSubject],
      options,
    };
  });
}
