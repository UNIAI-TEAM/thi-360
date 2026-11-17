/**
 * Vị trí của một thay đổi giữa hai phiên bản (`ExamVersionChange.where`). Máy chủ ghi đường dẫn trong bản chụp cấu hình,
 * ví dụ `config/durationMinutes` hay `parts/parts/<id phần>/sections/<id môn>/blocks/<id khối>/name`: đoạn đầu là gốc của
 * bản chụp (`config` hoặc `parts`), tên bộ sưu tập (`parts`, `sections`, `blocks`, `options`) đứng trước id của phần tử,
 * các đoạn sau là tên trường. Màn không in đường dẫn thô mà dựng nhãn từ cấu trúc (`useChangeWhere`).
 */

export type ChangeLevel = "part" | "section" | "block" | "option";

export interface ChangeStep {
  level: ChangeLevel;
  /** Id (hoặc chỉ số) của phần tử; `null` khi vị trí là cả bộ sưu tập (đổi thứ tự). */
  id: string | null;
}

export interface ChangeTarget {
  /** `true`: thay đổi ở cấu hình chung của bài (`config/...`), không ở cấu trúc. */
  config: boolean;
  steps: ChangeStep[];
  /** Tên trường theo thứ tự trong đường dẫn, không gồm chỉ số mảng. */
  fields: string[];
}

const LEVEL_OF_COLLECTION: Readonly<Record<string, ChangeLevel>> = {
  parts: "part",
  sections: "section",
  blocks: "block",
  options: "option",
};

/** `null` khi `where` không phải đường dẫn của bản chụp (ví dụ chữ mô tả của dữ liệu mẫu): giữ nguyên để hiển thị. */
export function parseChangeWhere(where: string): ChangeTarget | null {
  const [root, ...rest] = where.split("/");
  if (root !== "config" && root !== "parts") return null;
  const steps: ChangeStep[] = [];
  const fields: string[] = [];
  if (root === "parts" && rest.length === 0) return { config: false, steps: [{ level: "part", id: null }], fields };
  let index = 0;
  // Gốc `parts` chứa bộ sưu tập `parts`; đường dẫn đi thẳng vào trường thì không có bước nào.
  while (root === "parts" && index < rest.length) {
    const level = LEVEL_OF_COLLECTION[rest[index]!];
    if (!level) break;
    const id = rest[index + 1];
    const named = id !== undefined && LEVEL_OF_COLLECTION[id] === undefined;
    steps.push({ level, id: named ? id : null });
    index += named ? 2 : 1;
  }
  for (const segment of rest.slice(index)) {
    if (!/^\d+$/.test(segment)) fields.push(segment);
  }
  return { config: root === "config", steps, fields };
}
