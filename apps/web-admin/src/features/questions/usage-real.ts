import type { components } from "@thi360/api-client";
import { toContentApiError } from "@/features/content/api-error";
import { createBrowserApiClient } from "@/lib/api/client";
import type { ExamStatus } from "@/features/exams/types";
import type { QuestionUsage, QuestionUsageBlock, QuestionUsageExam as UsageExam } from "./editor-types";
import type { QuestionRetireImpact, QuestionUsageExam as RetireImpactExam } from "./types";

/**
 * Nơi đang dùng và ảnh hưởng khi ngừng câu theo API hiện hành: mỗi dòng nguồn (`QuestionbankExamSourceRef`) mang mã,
 * tên, trạng thái bài thi, phần, lựa chọn (tổ hợp) và khối lấy câu; các dòng sổ đã dùng (loại, bài, phiên bản) cho
 * lượt thi. Nhiều khối của cùng một bài gộp thành một dòng bài. Chỉ bài chỉ có trong sổ (không còn khối nguồn) hoặc
 * thiếu thông tin trong dòng nguồn mới đọc thêm chi tiết bài; `servingVersion` của retire-impact luôn lấy từ chi tiết
 * bài vì dòng nguồn không mang. Ô thiếu hụt (`needed`) chưa có trong API nên để `null`, giao diện bỏ qua.
 */

type ApiSourceRef = components["schemas"]["QuestionbankExamSourceRef"];
type ApiLedgerRef = components["schemas"]["LedgerRef"];
type ApiExamResponse = components["schemas"]["ExamDetailResponse"];

interface ExamBrief {
  code: string;
  name: string;
  status: ExamStatus;
  servingVersion: number | null;
}

const EXAM_STATUSES: readonly ExamStatus[] = ["DRAFT", "PUBLISHED", "DISCONTINUED"];

function toStatus(value: string | undefined): ExamStatus | null {
  return value !== undefined && (EXAM_STATUSES as readonly string[]).includes(value) ? (value as ExamStatus) : null;
}

function toBrief(exam: ApiExamResponse): ExamBrief {
  return {
    code: exam.code,
    name: exam.name,
    status: toStatus(exam.status) ?? "DRAFT",
    servingVersion: exam.servingVersion ?? null,
  };
}

/** Một bài và các khối nguồn của nó (rỗng khi bài chỉ có trong sổ). */
interface ExamSources {
  examId: string;
  refs: ApiSourceRef[];
}

function groupByExam(refs: readonly ApiSourceRef[], ledger: readonly ApiLedgerRef[]): ExamSources[] {
  const byExam = new Map<string, ExamSources>();
  for (const ref of refs) {
    const entry = byExam.get(ref.examId) ?? { examId: ref.examId, refs: [] };
    entry.refs.push(ref);
    byExam.set(ref.examId, entry);
  }
  for (const ref of ledger) {
    if (!byExam.has(ref.examId)) byExam.set(ref.examId, { examId: ref.examId, refs: [] });
  }
  return [...byExam.values()];
}

/** Bài thiếu mã, tên hoặc trạng thái trong dòng nguồn (bài chỉ có trong sổ, hoặc máy chủ cũ). */
function isComplete(sources: ExamSources): boolean {
  return sources.refs.length > 0 && sources.refs.every((ref) => ref.examCode && ref.examName && toStatus(ref.examStatus));
}

/** Chi tiết từng bài; không đọc được (thiếu quyền xem bài thi, bài đã xóa) thì dùng id làm mã và để trống tên. */
async function loadExamBriefs(examIds: readonly string[]): Promise<Map<string, ExamBrief>> {
  const client = createBrowserApiClient();
  const entries = await Promise.all(
    examIds.map(async (id): Promise<[string, ExamBrief]> => {
      const { data, response } = await client.GET("/api/v1/admin/exams/{id}", { params: { path: { id } } });
      const brief = response.ok && data ? toBrief(data) : { code: id, name: "", status: "DRAFT" as const, servingVersion: null };
      return [id, brief];
    }),
  );
  return new Map(entries);
}

/** Mã, tên, trạng thái: ưu tiên dòng nguồn, thiếu thì lấy từ chi tiết bài. */
function examIdentity(sources: ExamSources, brief: ExamBrief | undefined): Omit<ExamBrief, "servingVersion"> {
  const first = sources.refs.find((ref) => ref.examCode || ref.examName || ref.examStatus);
  return {
    code: first?.examCode || brief?.code || sources.examId,
    name: first?.examName || brief?.name || "",
    status: toStatus(first?.examStatus) ?? brief?.status ?? "DRAFT",
  };
}

function toBlock(ref: ApiSourceRef): QuestionUsageBlock {
  return {
    partName: ref.partName ?? "",
    optionCode: ref.optionCode ?? null,
    optionName: ref.optionName ?? "",
    blockName: ref.blockName ?? "",
  };
}

function uniqueBlocks(refs: readonly ApiSourceRef[]): QuestionUsageBlock[] {
  const seen = new Set<string>();
  const blocks: QuestionUsageBlock[] = [];
  for (const ref of refs) {
    const block = toBlock(ref);
    const key = JSON.stringify(block);
    if (seen.has(key)) continue;
    seen.add(key);
    blocks.push(block);
  }
  return blocks.filter((block) => block.partName || block.optionName || block.optionCode || block.blockName);
}

async function fetchUsage(id: string, kind: "usage" | "retire-impact"): Promise<{ exams: ExamSources[]; ledger: ApiLedgerRef[] }> {
  const client = createBrowserApiClient();
  if (kind === "usage") {
    const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}/usage", { params: { path: { id } } });
    if (!response.ok || !data) throw toContentApiError(response.status, error);
    return { exams: groupByExam(data.sourceBlocks, data.usedIn), ledger: data.usedIn };
  }
  const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}/retire-impact", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return { exams: groupByExam(data.affectedBlocks, data.usedIn), ledger: data.usedIn };
}

/** Môn và dạng của câu, để điền cột "Môn" của dòng bài thi. */
export interface UsageQuestionContext {
  subjectCode: string;
  type: RetireImpactExam["questionType"];
}

export async function loadQuestionUsage(id: string, context: UsageQuestionContext): Promise<QuestionUsage> {
  const { exams: sources, ledger } = await fetchUsage(id, "usage");
  const briefs = await loadExamBriefs(sources.filter((entry) => !isComplete(entry)).map((entry) => entry.examId));
  const exams: UsageExam[] = sources.map((entry) => {
    const identity = examIdentity(entry, briefs.get(entry.examId));
    const blocks = uniqueBlocks(entry.refs);
    const attempts = ledger.filter((ref) => ref.examId === entry.examId && ref.kind === "ATTEMPT");
    return {
      examId: entry.examId,
      examCode: identity.code,
      examName: identity.name,
      examStatus: identity.status,
      partName: blocks[0]?.partName ?? "",
      subjectCode: context.subjectCode,
      blockName: blocks[0]?.blockName ?? "",
      blocks,
      attemptsByVersion: attempts
        .map((ref) => ({ version: ref.versionNo, attemptCount: 1 }))
        .sort((left, right) => right.version - left.version),
    };
  });
  return {
    questionId: id,
    examCount: exams.length,
    attemptCount: exams.reduce((total, exam) => total + exam.attemptsByVersion.length, 0),
    exams,
  };
}

export async function loadRetireImpact(id: string, context: UsageQuestionContext): Promise<QuestionRetireImpact> {
  const { exams: sources, ledger } = await fetchUsage(id, "retire-impact");
  // servingVersion không có trong dòng nguồn: luôn đọc chi tiết bài.
  const briefs = await loadExamBriefs(sources.map((entry) => entry.examId));
  const exams: RetireImpactExam[] = sources.map((entry) => {
    const brief = briefs.get(entry.examId);
    const identity = examIdentity(entry, brief);
    const optionCodes = [...new Set(entry.refs.map((ref) => ref.optionCode).filter((code): code is string => Boolean(code)))];
    return {
      examId: entry.examId,
      examCode: identity.code,
      examName: identity.name,
      examStatus: identity.status,
      servingVersion: brief?.servingVersion ?? null,
      subjectCode: context.subjectCode,
      questionType: context.type,
      optionCode: optionCodes.length > 0 ? optionCodes.join(", ") : null,
      needed: null,
      available: 0,
    };
  });
  return { questionId: id, exams, hardDeleteBlocked: ledger.length > 0 };
}
