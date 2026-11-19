import { screen, within } from "@testing-library/react";
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

export const textboxByName = (name: string) => screen.findByRole("textbox", { name });

/** Ô số của một hàng Cấu trúc câu: nhóm `role="group"` cùng tên chứa đúng một ô nhập. */
export const structureInput = (name: string) => within(screen.getByRole("group", { name })).getByRole("textbox");
