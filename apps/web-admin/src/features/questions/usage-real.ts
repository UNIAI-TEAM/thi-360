import type { components } from "@thi360/api-client";
import { toContentApiError } from "@/features/content/api-error";
import { createBrowserApiClient } from "@/lib/api/client";
import type { ExamStatus } from "@/features/exams/types";
import type { QuestionUsage, QuestionUsageExam as UsageExam } from "./editor-types";
import type { QuestionRetireImpact, QuestionUsageExam as RetireImpactExam } from "./types";

/**
 * Nơi đang dùng và ảnh hưởng khi ngừng câu, theo hình dạng API hiện có (trước BE-6): máy chủ chỉ trả id bài thi có khối
 * lấy câu làm nguồn và các dòng sổ đã dùng (loại, bài, phiên bản). Mã, tên, trạng thái bài lấy từ chi tiết bài; tên
 * phần, khối, ô thiếu hụt chưa có nên để trống (`needed = null`), giao diện bỏ qua phần trống.
 */

type ApiUsage = components["schemas"]["QuestionUsage"];
type ApiLedgerRef = components["schemas"]["LedgerRef"];
type ApiExamResponse = components["schemas"]["ExamDetailResponse"];

interface ExamBrief {
  code: string;
  name: string;
  status: ExamStatus;
  servingVersion: number | null;
}

const EXAM_STATUSES: readonly ExamStatus[] = ["DRAFT", "PUBLISHED", "DISCONTINUED"];

function toBrief(exam: ApiExamResponse): ExamBrief {
  return {
    code: exam.code,
    name: exam.name,
    status: (EXAM_STATUSES as readonly string[]).includes(exam.status) ? (exam.status as ExamStatus) : "DRAFT",
    servingVersion: exam.servingVersion ?? null,
  };
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

async function fetchUsage(id: string, kind: "usage" | "retire-impact"): Promise<{ examIds: string[]; ledger: ApiLedgerRef[] }> {
  const client = createBrowserApiClient();
  if (kind === "usage") {
    const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}/usage", { params: { path: { id } } });
    if (!response.ok || !data) throw toContentApiError(response.status, error);
    return { examIds: examIdsOf(data), ledger: data.usedIn };
  }
  const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}/retire-impact", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  const sources = data.affectedBlocks.map((block) => block.examId);
  return { examIds: unique([...sources, ...data.usedIn.map((ref) => ref.examId)]), ledger: data.usedIn };
}

function examIdsOf(usage: ApiUsage): string[] {
  return unique([...usage.sourceBlocks.map((block) => block.examId), ...usage.usedIn.map((ref) => ref.examId)]);
}

function unique(values: readonly string[]): string[] {
  return [...new Set(values)];
}

/** Môn và dạng của câu, để điền cột "Môn" của dòng bài thi. */
export interface UsageQuestionContext {
  subjectCode: string;
  type: RetireImpactExam["questionType"];
}

export async function loadQuestionUsage(id: string, context: UsageQuestionContext): Promise<QuestionUsage> {
  const { examIds, ledger } = await fetchUsage(id, "usage");
  const briefs = await loadExamBriefs(examIds);
  const exams: UsageExam[] = examIds.map((examId) => {
    const brief = briefs.get(examId)!;
    const attempts = ledger.filter((ref) => ref.examId === examId && ref.kind === "ATTEMPT");
    return {
      examId,
      examCode: brief.code,
      examName: brief.name,
      examStatus: brief.status,
      partName: "",
      subjectCode: context.subjectCode,
      blockName: "",
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
  const { examIds, ledger } = await fetchUsage(id, "retire-impact");
  const briefs = await loadExamBriefs(examIds);
  const exams: RetireImpactExam[] = examIds.map((examId) => {
    const brief = briefs.get(examId)!;
    return {
      examId,
      examCode: brief.code,
      examName: brief.name,
      examStatus: brief.status,
      servingVersion: brief.servingVersion,
      subjectCode: context.subjectCode,
      questionType: context.type,
      optionCode: null,
      needed: null,
      available: 0,
    };
  });
  return { questionId: id, exams, hardDeleteBlocked: ledger.length > 0 };
}
