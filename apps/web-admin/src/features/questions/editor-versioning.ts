import type { PersonRef, QuestionStatus } from "@/features/content/types";
import type { QuestionSnapshot, QuestionVersionState } from "./editor-types";
import type { QuestionDetail } from "./types";

/**
 * Quy tắc phiên bản câu hỏi (UniNote PBI-03 mục 7.3, canvas `CauHoiPhienBan`), dạng hàm thuần để kiểm thử.
 *
 * - Sửa câu Nháp: sửa tại chỗ, không tạo phiên bản.
 * - Sửa câu Sẵn sàng (hoặc Ngừng sử dụng): tạo phiên bản mới ở trạng thái Nháp; phiên bản Sẵn sàng trước đó vẫn
 *   dùng cho lượt thi mới tới khi bản mới được chuyển sang Sẵn sàng. Lượt thi đã làm giữ phiên bản đã dùng.
 */
export function editCreatesNewVersion(question: { status: QuestionStatus }): boolean {
  return question.status !== "DRAFT";
}

export const SNAPSHOT_FIELDS = [
  "content",
  "body",
  "children",
  "solution",
  "points",
  "subjectCode",
  "gradeCode",
  "difficultyCode",
  "competencyCode",
  "knowledge",
  "tagCodes",
] as const satisfies readonly (keyof QuestionSnapshot)[];

export type SnapshotField = (typeof SNAPSHOT_FIELDS)[number];

export function toSnapshot(question: QuestionSnapshot): QuestionSnapshot {
  return structuredClone({
    subjectCode: question.subjectCode,
    gradeCode: question.gradeCode,
    difficultyCode: question.difficultyCode,
    competencyCode: question.competencyCode ?? null,
    points: question.points,
    knowledge: question.knowledge,
    tagCodes: question.tagCodes,
    content: question.content,
    body: question.body,
    solution: question.solution,
    children: question.children,
  });
}

/** Một trường khác nhau giữa hai phiên bản; giao diện tự định dạng giá trị theo `field`. */
export type QuestionVersionChange = {
  [K in SnapshotField]: { field: K; before: QuestionSnapshot[K]; after: QuestionSnapshot[K] };
}[SnapshotField];

/** Bỏ id nội bộ (id phương án, id câu con) để chỉ so nội dung. */
function comparable(field: SnapshotField, value: QuestionSnapshot[SnapshotField]): string {
  if (field === "children") {
    const children = value as QuestionDetail[];
    return JSON.stringify(
      children.map((child) => ({
        type: child.type,
        content: child.content,
        points: child.points,
        solution: child.solution,
        body: comparableBody(child.body),
      })),
    );
  }
  if (field === "body") return JSON.stringify(comparableBody(value as QuestionDetail["body"]));
  if (field === "knowledge") {
    return JSON.stringify(
      (value as QuestionDetail["knowledge"]).map((ref) => ({ nodeId: ref.nodeId, primary: ref.primary })),
    );
  }
  if (field === "solution") return JSON.stringify((value as string | null)?.trim() || null);
  if (field === "competencyCode") return JSON.stringify((value as string | null | undefined) || null);
  return JSON.stringify(value);
}

function comparableBody(body: QuestionDetail["body"]): unknown {
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return {
        type: body.type,
        options: body.options.map((option) => [option.content, option.correct]),
        shuffleOptions: body.shuffleOptions,
        scoringRule: body.type === "MULTIPLE_CHOICE" ? body.scoringRule : null,
      };
    case "TRUE_FALSE":
      return {
        type: body.type,
        statements: body.statements.map((s) => [s.content, s.answer]),
        scoreTable: body.scoreTable ?? [],
      };
    case "FILL_IN":
      return { type: body.type, blanks: body.blanks.map((blank) => blank.acceptedAnswers) };
    case "DRAG_DROP": {
      const contentOf = new Map(body.items.map((item) => [item.id, item.content]));
      const used = new Set(body.zones.map((zone) => zone.correctItemId));
      return {
        type: body.type,
        zones: body.zones.map((zone) => [zone.label, contentOf.get(zone.correctItemId) ?? ""]),
        distractors: body.items.filter((item) => !used.has(item.id)).map((item) => item.content),
      };
    }
    case "QUESTION_SET":
      return { type: body.type, passage: body.passage };
    case "ESSAY":
      return {
        type: body.type,
        maxPoints: body.maxPoints,
        rubric: body.rubric.map((criterion) => [criterion.description, criterion.points]),
        sampleAnswer: body.sampleAnswer?.trim() || null,
        wordLimit: body.wordLimit,
      };
  }
}

/** Các trường khác nhau giữa hai phiên bản, theo thứ tự `SNAPSHOT_FIELDS`. */
export function diffSnapshots(before: QuestionSnapshot, after: QuestionSnapshot): QuestionVersionChange[] {
  const changes: QuestionVersionChange[] = [];
  for (const field of SNAPSHOT_FIELDS) {
    if (comparable(field, before[field]) !== comparable(field, after[field])) {
      changes.push({ field, before: before[field], after: after[field] } as QuestionVersionChange);
    }
  }
  return changes;
}

/** Bản ghi lịch sử lưu trong kho mẫu; trạng thái hiển thị tính lại mỗi lần đọc theo trạng thái câu hỏi. */
export interface QuestionHistoryEntry {
  version: number;
  savedAt: string;
  savedBy: PersonRef;
  /** Lúc phiên bản này được chuyển sang Sẵn sàng; `null` khi chưa từng Sẵn sàng. */
  readyAt: string | null;
  snapshot: QuestionSnapshot;
}

/**
 * Trạng thái của từng phiên bản (mới nhất trước), suy ra từ trạng thái hiện tại của câu:
 * phiên bản mới nhất theo trạng thái câu (Nháp → đang sửa, Sẵn sàng → hiện hành, Ngừng → ngừng); khi câu đang có
 * bản Nháp, phiên bản Sẵn sàng gần nhất trước đó vẫn là bản hiện hành; còn lại là đã thay thế.
 */
export function deriveVersionStates(
  entries: readonly Pick<QuestionHistoryEntry, "version" | "readyAt">[],
  question: { version: number; status: QuestionStatus },
): Map<number, QuestionVersionState> {
  const states = new Map<number, QuestionVersionState>();
  const sorted = [...entries].sort((a, b) => b.version - a.version);
  let servingFound = false;
  for (const entry of sorted) {
    if (entry.version === question.version) {
      const state: QuestionVersionState =
        question.status === "READY" ? "CURRENT" : question.status === "RETIRED" ? "RETIRED" : "WORKING";
      states.set(entry.version, state);
      if (state !== "WORKING") servingFound = true;
      continue;
    }
    if (!servingFound && question.status === "DRAFT" && entry.readyAt !== null) {
      states.set(entry.version, "CURRENT");
      servingFound = true;
      continue;
    }
    states.set(entry.version, "SUPERSEDED");
  }
  return states;
}

/** Phiên bản đang phục vụ lượt thi mới (Sẵn sàng); `null` khi chưa có bản nào Sẵn sàng. */
export function servingVersion(states: ReadonlyMap<number, QuestionVersionState>): number | null {
  for (const [version, state] of states) if (state === "CURRENT") return version;
  return null;
}
