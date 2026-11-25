import { screen, within } from "@testing-library/react";
import viMessages from "@/messages/vi.json";
import { type Wire, wireBlock, wireSection } from "./subject-form-harness";

/**
 * Dữ liệu wire của bài QDA, BCA, V-ACT cho các test `subject-matrix-*` trên `StructureEditor` thật (CR-2). Mỗi môn đúng như mẫu của kỳ:
 * Khoa học 21 đơn + 1 chùm × 3 + 1 điền (T = 24, 25 câu), BCA Sử 7 đơn + 1 chùm × 3 (T = 10).
 */
export const matrixOf = (over: Wire = {}): Wire => ({
  mode: "SEPARATE",
  unit: "COUNT",
  cells: [],
  byGrade: [],
  byDifficulty: [],
  ...over,
});

interface ScienceOptions {
  single?: number;
  sets?: number;
  fill?: number;
  matrix?: Wire | null;
  section?: Wire;
}

/** Môn Khoa học của QDA: nhóm đơn, nhóm chùm cỡ 3, nhóm điền; thiếu `fill` (0 nhóm) khi `fill` là `undefined` và `noFill`. */
export function wireQdaScience(id: string, subjectCode: string, { single = 21, sets = 1, fill = 1, matrix = null, section = {} }: ScienceOptions = {}): Wire {
  return wireSection(
    id,
    subjectCode,
    [
      wireBlock(`${id}-single`, { name: `${id} don`, questionCount: single, templateGroupCode: `${subjectCode}_SINGLE` }),
      wireBlock(`${id}-set`, { name: `${id} chum`, questionType: "QUESTION_SET", setCount: sets, setSize: 3, questionCount: sets * 3, templateGroupCode: `${subjectCode}_SET` }),
      wireBlock(`${id}-fill`, { name: `${id} dien`, questionType: "SHORT_ANSWER", questionCount: fill, templateGroupCode: `${subjectCode}_FILL` }),
    ],
    { matrix, ...section },
  );
}

/** BCA Sử: 7 câu đơn + 1 chùm × 3, T = 10; không có nhóm điền. */
export function wireBcaHistory(id = "sec-su", over: Wire = {}): Wire {
  return wireSection(
    id,
    "SU",
    [
      wireBlock(`${id}-single`, { name: "Su don", questionCount: 7, templateGroupCode: "SU_SINGLE", requiredPointsPerQuestion: 1 }),
      wireBlock(`${id}-set`, { name: "Su chum", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, tagCodes: ["SU_TU_LIEU_LICH_SU"], templateGroupCode: "SU_SET", requiredPointsPerQuestion: 1 }),
    ],
    over,
  );
}

const structureMessages = viMessages.exams.subjectForm.matrix.structure;
const prefixOf = (template: string) => template.replace("{name}", "");
const COUNT_PREFIX = prefixOf(structureMessages.rowCountAria);
const SETS_PREFIX = prefixOf(structureMessages.rowSetsAria);
const SET_SIZE_PREFIX = prefixOf(structureMessages.rowSetSizeAria);
const PERCENT_PREFIX = prefixOf(structureMessages.rowPercentAria);
const FILL_ROLE = structureMessages.fill;
const SETS_ROLE = structureMessages.sets;
const CHOICE_ROLE = structureMessages.choice;
const SINGLE_ROLE = structureMessages.single;
const FILL_DEFAULT = structureMessages.defaultRowName.SHORT_ANSWER;

export const MATRIX_STRUCTURE_HEADING = structureMessages.title;

export const textboxByName = (name: string) => screen.findByRole("textbox", { name });

/** Ô Số câu của một dòng thành phần v3. */
export const rowCountInput = (rowName: string | RegExp) =>
  screen.getByRole("textbox", {
    name: typeof rowName === "string" ? `${COUNT_PREFIX}${rowName}` : new RegExp(`^${COUNT_PREFIX}(?:${rowName.source})$`, rowName.flags),
  });

/** Ô Số chùm của dòng `QUESTION_SET` cỡ cố định. */
export const rowSetsInput = (rowName?: string | RegExp) =>
  screen.getByRole("textbox", {
    name:
      rowName === undefined
        ? new RegExp(`^${SETS_PREFIX}`)
        : typeof rowName === "string"
          ? `${SETS_PREFIX}${rowName}`
          : new RegExp(`^${SETS_PREFIX}(?:${rowName.source})$`, rowName.flags),
  });

/** Ô Số câu/chùm của dòng `QUESTION_SET` cỡ cố định. */
export const rowSetSizeInput = (rowName?: string | RegExp) =>
  screen.getByRole("textbox", {
    name:
      rowName === undefined
        ? new RegExp(`^${SET_SIZE_PREFIX}`)
        : typeof rowName === "string"
          ? `${SET_SIZE_PREFIX}${rowName}`
          : new RegExp(`^${SET_SIZE_PREFIX}(?:${rowName.source})$`, rowName.flags),
  });

/** Ô Tỷ lệ (%) của dòng thành phần ở kỳ nhập 2 cột (V-ACT). */
export const rowPercentInput = (rowName: string | RegExp) =>
  screen.getByRole("textbox", {
    name: typeof rowName === "string" ? `${PERCENT_PREFIX}${rowName}` : new RegExp(`^${PERCENT_PREFIX}(?:${rowName.source})$`, rowName.flags),
  });

/** Tương thích với các test cũ: ánh xạ nhãn vai trò sang ô dòng tương ứng của bảng v3. */
export const structureInput = (name: string) => {
  if (name === SETS_ROLE) return rowSetsInput();
  if (name === FILL_ROLE) return screen.getByRole("textbox", { name: new RegExp(`^${COUNT_PREFIX}.*(${FILL_ROLE}|${FILL_DEFAULT}|dien)`, "i") });
  if (name === CHOICE_ROLE || name === SINGLE_ROLE) {
    return screen.getByRole("textbox", { name: new RegExp(`^${COUNT_PREFIX}.*(${CHOICE_ROLE}|${SINGLE_ROLE}|don)`, "i") });
  }
  return within(screen.getByRole("group", { name })).getByRole("textbox");
};
