import type { components } from "@thi360/api-client";
import type {
  BulkQuestionStatusResult,
  PageResponseQuestionSummary,
  QuestionDetail,
  QuestionListItem,
  QuestionStatusCounts,
  QuestionStatusSkipReason,
  QuestionSummary,
} from "./types";
import { asWireBody, fromWireBody } from "./wire-body";

/**
 * Chuyển phản hồi API câu hỏi (kiểu sinh từ OpenAPI, nguồn đúng) sang kiểu của web trong `types.ts`. Kiểu của web
 * giữ phẳng và không có `undefined` (trường thiếu thành `null` hoặc chuỗi rỗng) để giao diện không đổi.
 */

type ApiSummary = components["schemas"]["QuestionSummary"];
type ApiDetail = components["schemas"]["QuestionDetail"];
type ApiPage = components["schemas"]["PageResponseQuestionSummary"];
type ApiCounts = components["schemas"]["QuestionStatusCounts"];
type ApiBulkResult = components["schemas"]["BulkQuestionStatusResult"];

const NO_PERSON = { id: "", name: "" } as const;

export function fromApiSummary(summary: ApiSummary): QuestionSummary {
  return {
    id: summary.id,
    code: summary.code,
    type: summary.type,
    status: summary.status,
    contentPreview: summary.contentPreview,
    subjectCode: summary.subjectCode ?? "",
    gradeCode: summary.gradeCode ?? "",
    difficultyCode: summary.difficultyCode ?? "",
    points: summary.points,
    knowledge: summary.knowledge.map((ref) => ({
      nodeId: ref.nodeId,
      code: ref.code ?? "",
      name: ref.name ?? "",
      primary: ref.primary,
    })),
    competencyCode: summary.competencyCode ?? null,
    taskTypeCode: summary.taskTypeCode ?? null,
    tagCodes: summary.tagCodes,
    hasSolution: summary.hasSolution,
    // Số phiên bản mới nhất: bản Nháp đang sửa nếu có, không thì bản đang phục vụ; câu mới có bản 1.
    version: Math.max(summary.version, summary.workingVersion ?? 0, 1),
    revision: summary.revision,
    usedInExamCount: summary.usedInExamCount,
    sourceCode: summary.sourceCode ?? null,
    createdBy: summary.createdBy ?? NO_PERSON,
    createdAt: summary.createdAt,
    updatedAt: summary.updatedAt,
  };
}

/** Lỗi sai thường gặp là một ô HTML ở web; máy chủ lưu danh sách, nên ghép lại khi đọc và gói một phần tử khi ghi. */
export function joinCommonMistakes(mistakes: readonly string[]): string | null {
  const joined = mistakes.join("");
  return joined === "" ? null : joined;
}

export function fromApiDetail(detail: ApiDetail): QuestionDetail {
  const children = detail.children.map(fromApiDetail);
  return {
    ...fromApiSummary(detail.summary),
    content: detail.content,
    body: fromWireBody(
      asWireBody(detail.body),
      children.map((child) => child.id),
    ),
    solution: detail.solution ?? null,
    commonMistakes: joinCommonMistakes(detail.commonMistakes),
    attachmentIds: detail.attachmentIds,
    children,
  };
}

export function fromApiPage(page: ApiPage): PageResponseQuestionSummary {
  const content: QuestionListItem[] = page.items.map(fromApiSummary);
  return {
    content,
    page: page.page,
    size: page.size,
    totalElements: page.totalItems,
    totalPages: page.totalPages,
  };
}

export function fromApiCounts(counts: ApiCounts): QuestionStatusCounts {
  return {
    total: counts.draft + counts.ready + counts.retired,
    byStatus: { DRAFT: counts.draft, READY: counts.ready, RETIRED: counts.retired },
  };
}

const SKIP_REASONS: readonly QuestionStatusSkipReason[] = ["NOT_READY", "ALREADY_IN_STATUS", "NOT_FOUND"];

/**
 * Kết quả đổi trạng thái hàng loạt. Dòng bị bỏ qua chỉ có id: lấy mã từ `codes` (danh sách đã chọn) nếu có, không thì
 * dùng id làm mã hiển thị.
 */
export function fromApiBulkResult(result: ApiBulkResult, codes: ReadonlyMap<string, string> = new Map()): BulkQuestionStatusResult {
  return {
    succeeded: result.updated.map(fromApiSummary),
    skipped: result.skipped.map((item) => ({
      id: item.questionId,
      code: codes.get(item.questionId) ?? item.questionId,
      reason: (SKIP_REASONS as readonly string[]).includes(item.reason)
        ? (item.reason as QuestionStatusSkipReason)
        : "NOT_READY",
    })),
  };
}
