import type { ExamPathRef, PathChoice } from "./review-types";
import type { ExamDetail, ExamPart } from "./types";

/**
 * Tiện ích thuần còn dùng trên màn thật: liệt kê đường lựa chọn (xem trước đề) và cờ "năm cấu trúc khác năm
 * ma trận cần chuyên gia xác nhận". Bộ kiểm tra cấu hình phía client đã bị gỡ cùng lớp mock — server kiểm tra
 * thật qua `review-api.ts`.
 */

/** Giới hạn dài hơn khi người dùng chọn "Kiểm tra lại với thời gian dài hơn" (mẫu gửi server). */
export const EXTENDED_CHECK_TIME_LIMIT_SECONDS = 120;

export type ChoiceNameResolver = (kind: PathChoice["kind"], code: string) => string;

const codeAsName: ChoiceNameResolver = (_kind, code) => code;

const DEFAULT_PATH_ID = "default";

export function sortedParts(parts: readonly ExamPart[]): ExamPart[] {
  return [...parts].sort((a, b) => a.order - b.order);
}

/** Liệt kê mọi đường lựa chọn: tích Descartes các lựa chọn của mọi phần tự chọn (theo thứ tự phần). */
export function enumeratePaths(parts: readonly ExamPart[], nameOf: ChoiceNameResolver = codeAsName): ExamPathRef[] {
  let combos: PathChoice[][] = [[]];
  for (const part of sortedParts(parts)) {
    if (part.mode !== "CHOICE" || part.options.length === 0) continue;
    combos = combos.flatMap((prefix) =>
      part.options.map((option) => [
        ...prefix,
        {
          partId: part.id,
          optionId: option.id,
          kind: option.kind,
          code: option.code,
          // Nhánh và tổ hợp sinh từ "chọn k trong n" (chưa có mã danh mục) mang tên riêng; còn lại lấy theo danh mục.
          name: option.name ?? (option.kind === "BRANCH" ? option.code : nameOf(option.kind, option.code)),
        },
      ]),
    );
  }
  return combos.map((choices) => ({
    pathId: choices.length ? choices.map((choice) => choice.optionId).join("+") : DEFAULT_PATH_ID,
    choices,
  }));
}

/** Năm cấu trúc khác năm ma trận (SP2E 2026 dùng ma trận 2025) mà chuyên gia chưa xác nhận chỉ tiêu. */
export function yearsNeedConfirmation(exam: Pick<ExamDetail, "structureYear" | "matrixYear" | "yearsConfirmed">): boolean {
  return (
    exam.structureYear !== null && exam.matrixYear !== null && exam.structureYear !== exam.matrixYear && !exam.yearsConfirmed
  );
}
