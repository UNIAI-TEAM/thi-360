import type { ExamDetail, ExamSection, QuestionBlock } from "@/features/exams/types";
import type { QuestionUsage, QuestionUsageExam } from "./editor-types";
import type { QuestionSummary } from "./types";

/**
 * Nơi đang dùng của câu hỏi trong dữ liệu mẫu. Bài thi mẫu chỉ có ma trận (sinh đề ngẫu nhiên), không lưu id câu,
 * nên quan hệ câu ↔ bài thi được suy ra cố định từ id: lấy `usedInExamCount` bài thi, ưu tiên bài có khối cùng môn
 * và cùng dạng câu, rồi cùng môn, rồi bất kỳ; thứ tự trong mỗi nhóm theo băm của (id câu, id bài). Số lượt thi chia
 * cố định theo băm. Khi nối backend, máy chủ trả quan hệ thật; hàm này bỏ đi.
 */

/** Băm FNV-1a 32 bit, đủ để xếp thứ tự cố định. */
export function stableHash(text: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

interface BlockLocation {
  partName: string;
  section: ExamSection;
  block: QuestionBlock;
}

function examBlocks(exam: ExamDetail): BlockLocation[] {
  const locations: BlockLocation[] = [];
  for (const part of exam.parts) {
    const sections = [...part.sections, ...part.options.flatMap((option) => option.sections)];
    for (const section of sections) {
      for (const block of section.blocks) locations.push({ partName: part.name, section, block });
    }
  }
  return locations;
}

function bestBlock(question: QuestionSummary, exam: ExamDetail): { location: BlockLocation | null; rank: number } {
  const blocks = examBlocks(exam);
  const sameSubject = blocks.filter((location) => location.section.subjectCode === question.subjectCode);
  const sameType = sameSubject.find((location) => location.block.questionType === question.type);
  if (sameType) return { location: sameType, rank: 0 };
  if (sameSubject[0]) return { location: sameSubject[0], rank: 1 };
  return { location: blocks[0] ?? null, rank: 2 };
}

/**
 * Chia số lượt thi của câu trong một bài cho các phiên bản đã Sẵn sàng (mới trước). Bài Nháp chưa có lượt thi.
 * Phiên bản mới nhận phần ít hơn vì mới được dùng gần đây.
 */
function splitAttempts(
  question: QuestionSummary,
  exam: ExamDetail,
  readyVersions: readonly number[],
): QuestionUsageExam["attemptsByVersion"] {
  if (readyVersions.length === 0) return [];
  const total = exam.status === "DRAFT" ? 0 : Math.round((exam.attemptCount * (40 + (stableHash(question.id + exam.id) % 45))) / 100);
  const result: QuestionUsageExam["attemptsByVersion"] = [];
  let remaining = total;
  readyVersions.forEach((version, index) => {
    const last = index === readyVersions.length - 1;
    const share = last ? remaining : Math.round(remaining * (0.3 + (stableHash(`${question.id}:${version}`) % 30) / 100));
    result.push({ version, attemptCount: share });
    remaining -= share;
  });
  return result;
}

export function deriveQuestionUsage(
  question: QuestionSummary,
  exams: readonly ExamDetail[],
  /** Phiên bản đã từng Sẵn sàng, mới trước (bản Nháp chưa dùng cho lượt thi nào). */
  readyVersions: readonly number[],
): QuestionUsage {
  const ranked = exams
    .map((exam) => ({ exam, ...bestBlock(question, exam), order: stableHash(question.id + exam.id) }))
    .filter((entry) => entry.location !== null)
    .sort((a, b) => a.rank - b.rank || a.order - b.order)
    .slice(0, Math.max(0, question.usedInExamCount));

  const usageExams: QuestionUsageExam[] = ranked.map(({ exam, location }) => ({
    examId: exam.id,
    examCode: exam.code,
    examName: exam.name,
    examStatus: exam.status,
    partName: location!.partName,
    subjectCode: location!.section.subjectCode,
    blockName: location!.block.name,
    attemptsByVersion: splitAttempts(question, exam, readyVersions),
  }));

  return {
    questionId: question.id,
    examCount: usageExams.length,
    attemptCount: usageExams.reduce(
      (sum, exam) => sum + exam.attemptsByVersion.reduce((inner, entry) => inner + entry.attemptCount, 0),
      0,
    ),
    exams: usageExams,
  };
}

/** Tổng lượt thi theo từng phiên bản trên mọi bài thi. */
export function attemptsPerVersion(usage: QuestionUsage): Map<number, number> {
  const totals = new Map<number, number>();
  for (const exam of usage.exams) {
    for (const entry of exam.attemptsByVersion) {
      totals.set(entry.version, (totals.get(entry.version) ?? 0) + entry.attemptCount);
    }
  }
  return totals;
}
