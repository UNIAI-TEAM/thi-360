import { MOCK_CURRENT_AUTHOR } from "@/features/questions/mock/store";
import { MockApiError, mockRequest } from "@/lib/mock/content-mock";
import { checkExam, CHECK_TIME_LIMIT_SECONDS, enumeratePaths, EXTENDED_CHECK_TIME_LIMIT_SECONDS } from "./check";
import { examStore } from "./mock/store";
import {
  mockAvailability,
  mockBlockCandidates,
  mockCheckDuration,
  mockChoiceName,
  mockKnowledgeChapter,
  mockKnowledgeNode,
  restrictParts,
  reviewStore,
} from "./mock/review-store";
import { generatePaper } from "./review-preview";
import type {
  ExamChange,
  ExamCheckResult,
  ExamPathRef,
  ExamVersionDetail,
  ExamVersionList,
  ExamVersionRow,
  GeneratePreviewRequest,
  PreviewResult,
  PublishExamRequest,
  PublishExamResponse,
  RunExamCheckRequest,
} from "./review-types";
import type { ExamDetail, ExamPart, ExamVersion } from "./types";

/**
 * API của Kiểm tra cấu hình, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11). Hiện chạy trên dữ liệu mẫu
 * (`mock/review-store.ts`, `examStore`, `questionStore`). Khi nối backend: thay thân từng hàm bằng lời gọi
 * `@thi360/api-client`, giữ nguyên chữ ký; lỗi nghiệp vụ trả `MockApiError` cùng `status` và `code` như API thật.
 */

export const EXAM_REVIEW_QUERY_KEYS = {
  all: ["exams", "review"] as const,
  check: (examId: string) => ["exams", "review", examId, "check"] as const,
  versions: (examId: string) => ["exams", "review", examId, "versions"] as const,
  version: (examId: string, version: number) => ["exams", "review", examId, "versions", version] as const,
};

function nowIso(): string {
  return new Date().toISOString();
}

function computeCheck(exam: ExamDetail, timeLimitSeconds: number, checkedAt: string): ExamCheckResult {
  const availability = mockAvailability(exam.id, exam.parts);
  const outcome = checkExam(exam, availability, { timeLimitSeconds, nameOf: mockChoiceName });
  return {
    ...outcome,
    examId: exam.id,
    checkedAt,
    durationSeconds: mockCheckDuration(availability, timeLimitSeconds),
    timeLimitSeconds,
  };
}

/** Kết quả kiểm tra gần nhất (dữ liệu mẫu tính lại lần đầu theo thời điểm đã lưu trên bài thi). */
function latestCheck(exam: ExamDetail): ExamCheckResult | null {
  const stored = reviewStore.getCheck(exam.id);
  if (stored) return stored;
  if (exam.check.status === "UNCHECKED" || !exam.check.checkedAt) return null;
  const seeded = computeCheck(exam, CHECK_TIME_LIMIT_SECONDS, exam.check.checkedAt);
  reviewStore.setCheck(seeded);
  return seeded;
}

export async function getExamCheck({ examId }: { examId: string }): Promise<ExamCheckResult | null> {
  return mockRequest(() => latestCheck(examStore.require(examId)), { empty: () => null });
}

/** Chạy kiểm tra trên nguồn câu hỏi hiện tại; cập nhật trạng thái kiểm tra của bài thi. */
export async function runExamCheck({ examId, extendedTimeLimit = false }: RunExamCheckRequest): Promise<ExamCheckResult> {
  return mockRequest(
    () => {
      const exam = examStore.require(examId);
      const limit = extendedTimeLimit ? EXTENDED_CHECK_TIME_LIMIT_SECONDS : CHECK_TIME_LIMIT_SECONDS;
      const result = computeCheck(exam, limit, nowIso());
      reviewStore.setCheck(result);
      examStore.update(examId, (current) => ({
        ...current,
        check: { status: result.status, issueCount: result.issueCount, checkedAt: result.checkedAt },
      }));
      return result;
    },
    { ignoreScenario: true },
  );
}

function partsOf(exam: ExamDetail, version: number | null | undefined): ExamPart[] {
  if (version === null || version === undefined) return exam.parts;
  const found = reviewStore.state(exam).versions.find((item) => item.version === version);
  if (!found) throw new MockApiError(404, "VERSION_NOT_FOUND");
  return found.parts;
}

/**
 * Sinh đề xem trước cho một đường lựa chọn bằng cùng quy tắc với kiểm tra (AC-04.11). Đường không hợp lệ hoặc chưa
 * xác nhận được thì không trả đề thiếu câu, không nới ma trận: trả `FAILED` kèm vấn đề.
 */
export async function generatePreview({ examId, pathId, seed, version = null }: GeneratePreviewRequest): Promise<PreviewResult> {
  return mockRequest(
    () => {
      const exam = examStore.require(examId);
      const parts = partsOf(exam, version);
      const availability = mockAvailability(exam.id, parts);
      const outcome = checkExam({ ...exam, parts }, availability, { nameOf: mockChoiceName });
      const path = outcome.paths.find((item) => item.pathId === pathId);
      if (!path) throw new MockApiError(404, "PATH_NOT_FOUND");
      const base = {
        examId,
        path: { pathId: path.pathId, choices: path.choices },
        version: version ?? null,
        seed,
        generatedAt: nowIso(),
      };
      if (path.result !== "VALID") {
        const issues = [...new Map(path.issues.map((issue) => [issue.key, issue])).values()];
        return { ...base, status: "FAILED" as const, reason: path.result, issues };
      }
      const paper = generatePaper({
        parts,
        path,
        availability,
        seed,
        candidates: mockBlockCandidates,
        knowledgeNode: mockKnowledgeNode,
        knowledgeChapter: mockKnowledgeChapter,
      });
      return { ...base, status: "GENERATED" as const, paper };
    },
    { ignoreScenario: true },
  );
}

/* ---------- Phiên bản ---------- */

function pathRefs(parts: readonly ExamPart[], pathIds: readonly string[]): ExamPathRef[] {
  const all = enumeratePaths(parts, mockChoiceName);
  return pathIds.map((pathId) => all.find((path) => path.pathId === pathId) ?? { pathId, choices: [] });
}

function versionRow(exam: ExamDetail, item: ExamVersion): ExamVersionRow | null {
  if (item.version === null) return null;
  const state = reviewStore.state(exam).versions.find((entry) => entry.version === item.version);
  const parts = state?.parts ?? exam.parts;
  return {
    ...item,
    version: item.version,
    publishedPaths: pathRefs(parts, state?.publishedPathIds ?? []),
    servingPaths: pathRefs(parts, state?.servingPathIds ?? []),
  };
}

function workingChanges(exam: ExamDetail, check: ExamCheckResult | null): ExamChange[] {
  if (reviewStore.state(exam).publishedInSession.length) return [];
  return reviewStore.fixtureChanges(exam.id).map(({ optionId, ...change }) => {
    const path = optionId ? check?.paths.find((item) => item.choices.some((choice) => choice.optionId === optionId)) : null;
    const warning = path?.result === "INVALID" ? "INVALID" : path?.result === "UNCONFIRMED" ? "UNCONFIRMED" : null;
    return { ...change, warning };
  });
}

/** Đường của bản làm việc còn chờ công bố: mọi đường, trừ đường đã công bố trong phiên. */
function unpublishedPathIds(exam: ExamDetail): string[] {
  const published = new Set(reviewStore.state(exam).publishedInSession);
  return enumeratePaths(exam.parts)
    .map((path) => path.pathId)
    .filter((pathId) => !published.has(pathId));
}

export async function listExamVersions({ examId }: { examId: string }): Promise<ExamVersionList> {
  return mockRequest(
    () => {
      const exam = examStore.require(examId);
      const check = latestCheck(exam);
      const working = exam.versions.find((item) => item.state === "WORKING");
      const items = exam.versions
        .map((item) => versionRow(exam, item))
        .filter((row): row is ExamVersionRow => row !== null)
        .sort((a, b) => b.version - a.version);
      return {
        examId,
        workingCopy: exam.hasUnpublishedChanges
          ? {
              editedBy: exam.updatedBy,
              savedAt: exam.updatedAt,
              baseVersion: exam.servingVersion,
              note: working?.note ?? null,
              check: exam.check,
              unpublishedPaths: pathRefs(exam.parts, unpublishedPathIds(exam)),
            }
          : null,
        changes: exam.hasUnpublishedChanges ? workingChanges(exam, check) : [],
        items,
      };
    },
    { empty: () => ({ examId, workingCopy: null, changes: [], items: [] }) },
  );
}

export async function getExamVersion({ examId, version }: { examId: string; version: number }): Promise<ExamVersionDetail> {
  return mockRequest(() => {
    const exam = examStore.require(examId);
    const item = exam.versions.find((entry) => entry.version === version);
    const row = item ? versionRow(exam, item) : null;
    if (!row) throw new MockApiError(404, "VERSION_NOT_FOUND");
    return { examId, row, parts: partsOf(exam, version) };
  });
}

/**
 * Công bố bản làm việc cho các đường đã chọn. Chỉ đường Hợp lệ trong lần kiểm tra gần nhất mới công bố được; đường
 * không chọn giữ phiên bản đang phục vụ. Lượt đang làm giữ bản chụp cũ (snapshot), chỉ lượt mới dùng phiên bản mới.
 */
export async function publishExam({ examId, pathIds, note }: PublishExamRequest): Promise<PublishExamResponse> {
  return mockRequest(
    () => {
      const exam = examStore.require(examId);
      if (!exam.hasUnpublishedChanges) throw new MockApiError(409, "NOTHING_TO_PUBLISH");
      if (pathIds.length === 0) throw new MockApiError(422, "NO_PATH_SELECTED");
      const check = latestCheck(exam);
      if (!check || exam.check.status === "NEEDS_RECHECK") throw new MockApiError(409, "CHECK_REQUIRED");
      for (const pathId of pathIds) {
        const path = check.paths.find((item) => item.pathId === pathId);
        if (!path || path.result !== "VALID") throw new MockApiError(422, "PATH_NOT_VALID");
      }

      const state = reviewStore.state(exam);
      const version = Math.max(0, ...exam.versions.map((item) => item.version ?? 0)) + 1;
      const selected = new Set(pathIds);
      const keptPaths: PublishExamResponse["keptPaths"] = [];
      const versions = state.versions.map((item) => {
        const servingPathIds = item.servingPathIds.filter((pathId) => !selected.has(pathId));
        for (const pathId of servingPathIds) keptPaths.push({ pathId, version: item.version });
        return { ...item, servingPathIds };
      });
      versions.push({
        version,
        parts: restrictParts(exam.parts, pathIds),
        publishedPathIds: [...pathIds],
        servingPathIds: [...pathIds],
      });
      const publishedInSession = [...new Set([...state.publishedInSession, ...pathIds])];
      const allPublished = enumeratePaths(exam.parts).every((path) => publishedInSession.includes(path.pathId));
      reviewStore.setState(examId, { versions, publishedInSession });

      const publishedAt = nowIso();
      examStore.update(examId, (current) => {
        const stillServing = new Set(versions.filter((item) => item.servingPathIds.length).map((item) => item.version));
        const history = current.versions
          .filter((item) => item.state !== "WORKING" || !allPublished)
          .map((item) =>
            item.state === "SERVING" && item.version !== null && !stillServing.has(item.version)
              ? { ...item, state: "RETIRED" as const }
              : item,
          );
        const published: ExamVersion = {
          version,
          state: "SERVING",
          publishedAt,
          publishedBy: MOCK_CURRENT_AUTHOR,
          attemptCount: 0,
          note: note?.trim() || null,
        };
        const working = history.filter((item) => item.state === "WORKING");
        const rest = history.filter((item) => item.state !== "WORKING");
        return {
          ...current,
          status: current.status === "DRAFT" ? "PUBLISHED" : current.status,
          servingVersion: version,
          hasUnpublishedChanges: !allPublished,
          versions: [...working, published, ...rest],
          updatedAt: publishedAt,
        };
      });
      return { examId, version, publishedPathIds: [...pathIds], keptPaths };
    },
    { ignoreScenario: true },
  );
}

/** Ngừng bán: không mở bán cho lượt mua mới; học viên đã mua vẫn làm được theo phiên bản đang phục vụ. */
export async function discontinueExam({ examId }: { examId: string }): Promise<{ examId: string }> {
  return mockRequest(
    () => {
      const exam = examStore.require(examId);
      if (exam.status !== "PUBLISHED") throw new MockApiError(409, "NOT_PUBLISHED");
      examStore.update(examId, (current) => ({ ...current, status: "DISCONTINUED", updatedAt: nowIso() }));
      return { examId };
    },
    { ignoreScenario: true },
  );
}

export interface ExamReviewApiDeps {
  getExamCheck: typeof getExamCheck;
  runExamCheck: typeof runExamCheck;
  generatePreview: typeof generatePreview;
  listExamVersions: typeof listExamVersions;
  getExamVersion: typeof getExamVersion;
  publishExam: typeof publishExam;
  discontinueExam: typeof discontinueExam;
}

export const defaultExamReviewApiDeps: ExamReviewApiDeps = {
  getExamCheck,
  runExamCheck,
  generatePreview,
  listExamVersions,
  getExamVersion,
  publishExam,
  discontinueExam,
};
