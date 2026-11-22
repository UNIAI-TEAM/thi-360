import { z } from "zod";
import { checkScoreInput, COMMENT_MAX, formatScoreInput, parseScore, sumScores } from "./scoring";
import type {
  FinalizeEssayRequest,
  GradingItemDetailResponse,
  SaveEssayDraftRequest,
} from "./types";

export interface GradingItemFormValues {
  criteria: string[];
  totalPoints: string;
  comment: string;
}

export type GradingValidationKey =
  | "invalid"
  | "tooHigh"
  | "required"
  | "criteriaSumMismatch"
  | "commentMax";

export interface FinalizeValidationIssue {
  field: `criteria.${number}` | "totalPoints" | "comment";
  code: GradingValidationKey;
  max?: number;
}

export function isItemFinalized(detail: GradingItemDetailResponse): boolean {
  return detail.state === "DONE" || detail.activeGrade?.state === "FINAL";
}

export function isItemClaimable(detail: GradingItemDetailResponse): boolean {
  if (isItemFinalized(detail)) return false;
  const active = detail.activeGrade;
  if (!active) return true;
  if (active.state === "DRAFT" && (!active.graderId || active.claimExpired === true)) return true;
  return detail.state === "WAITING" && (!active.graderId || active.claimExpired === true);
}

export function isItemClaimedByOther(
  detail: GradingItemDetailResponse,
  currentUserId: string | null,
): boolean {
  if (isItemFinalized(detail)) return false;
  const active = detail.activeGrade;
  if (!active || active.state !== "DRAFT" || !active.graderId) return false;
  if (active.claimExpired === true) return false;
  return active.graderId !== currentUserId;
}

export function isItemOwnedDraft(
  detail: GradingItemDetailResponse,
  currentUserId: string | null,
): boolean {
  if (isItemFinalized(detail)) return false;
  const active = detail.activeGrade;
  if (!active || active.state !== "DRAFT" || !active.graderId || !currentUserId) return false;
  if (active.claimExpired === true) return false;
  return active.graderId === currentUserId;
}

export function toItemFormValues(
  detail: GradingItemDetailResponse,
  decimalSeparator: string,
): GradingItemFormValues {
  const rubric = detail.rubric ?? [];
  const active = detail.activeGrade;
  const criteria = rubric.map((criterion) => {
    const existing = active?.criteria?.find((entry) => entry.id === criterion.id);
    return formatScoreInput(existing?.points ?? null, decimalSeparator);
  });

  let totalPoints = formatScoreInput(active?.points ?? null, decimalSeparator);
  if (totalPoints === "" && rubric.length > 0) {
    const filled = criteria.map(parseScore).filter((value) => Number.isFinite(value));
    if (filled.length > 0) {
      totalPoints = formatScoreInput(sumScores(filled), decimalSeparator);
    }
  }

  return {
    criteria,
    totalPoints,
    comment: active?.comment ?? "",
  };
}

/**
 * Schema for draft save: allows blank inputs while checking numeric range (`0..max`)
 * and comment max length.
 */
export function gradingDraftSchema(detail: GradingItemDetailResponse) {
  const rubric = detail.rubric ?? [];
  const pointsMax = detail.pointsMax ?? 0;
  return z
    .object({
      criteria: z.array(z.string()),
      totalPoints: z.string(),
      comment: z.string(),
    })
    .superRefine((values, ctx) => {
      rubric.forEach((criterion, index) => {
        const max = criterion.points ?? pointsMax;
        const issue = checkScoreInput(values.criteria[index] ?? "", max, false);
        if (issue) {
          ctx.addIssue({ code: "custom", path: ["criteria", index], message: issue });
        }
      });
      const totalIssue = checkScoreInput(values.totalPoints, pointsMax, false);
      if (totalIssue) {
        ctx.addIssue({ code: "custom", path: ["totalPoints"], message: totalIssue });
      }
      if (values.comment.length > COMMENT_MAX) {
        ctx.addIssue({ code: "custom", path: ["comment"], message: "commentMax" });
      }
    });
}

/**
 * Full client-side validation before finalizing:
 * - Every criterion (if rubric exists) is required and within `[0, criterion.points]`
 * - `totalPoints` is required and within `[0, pointsMax]`
 * - When `rubric.length > 0`, sum of criteria must equal `totalPoints` within `0.001` (AC-6)
 */
export function findFinalizeIssues(
  detail: GradingItemDetailResponse,
  values: GradingItemFormValues,
): FinalizeValidationIssue[] {
  const issues: FinalizeValidationIssue[] = [];
  const rubric = detail.rubric ?? [];
  const pointsMax = detail.pointsMax ?? 0;

  let allCriteriaValid = true;
  const parsedCriteria: number[] = [];

  rubric.forEach((criterion, index) => {
    const max = criterion.points ?? pointsMax;
    const raw = values.criteria[index] ?? "";
    const issue = checkScoreInput(raw, max, true);
    if (issue) {
      allCriteriaValid = false;
      issues.push({ field: `criteria.${index}`, code: issue, max });
    } else {
      parsedCriteria.push(parseScore(raw));
    }
  });

  const totalIssue = checkScoreInput(values.totalPoints, pointsMax, true);
  if (totalIssue) {
    issues.push({ field: "totalPoints", code: totalIssue, max: pointsMax });
  } else if (rubric.length > 0 && allCriteriaValid) {
    const criteriaSum = sumScores(parsedCriteria);
    const total = parseScore(values.totalPoints);
    if (Math.abs(criteriaSum - total) > 0.001) {
      issues.push({ field: "totalPoints", code: "criteriaSumMismatch" });
    }
  }

  if (values.comment.length > COMMENT_MAX) {
    issues.push({ field: "comment", code: "commentMax" });
  }

  return issues;
}

/** Compute displayed running sum from valid criteria string inputs. */
export function criteriaInputTotal(criteriaInputs: readonly string[] | undefined): number {
  return sumScores(
    (criteriaInputs ?? []).map((raw) => {
      const value = parseScore(raw);
      return Number.isFinite(value) ? value : null;
    }),
  );
}

export function toSaveDraftRequest(
  detail: GradingItemDetailResponse,
  values: GradingItemFormValues,
): SaveEssayDraftRequest {
  const rubric = detail.rubric ?? [];
  const existingCriteria = detail.activeGrade?.criteria ?? [];
  const trimmedTotal = values.totalPoints.trim();
  const parsedTotal = trimmedTotal === "" ? undefined : parseScore(trimmedTotal);

  const criteria =
    rubric.length > 0
      ? rubric.map((criterion, index) => {
          const raw = (values.criteria[index] ?? "").trim();
          const parsed = raw === "" ? undefined : parseScore(raw);
          const existingComment = existingCriteria.find((entry) => entry.id === criterion.id)?.comment;
          return {
            ...(criterion.id ? { id: criterion.id } : {}),
            ...(parsed !== undefined && Number.isFinite(parsed) ? { points: parsed } : {}),
            ...(existingComment ? { comment: existingComment } : {}),
          };
        })
      : undefined;

  return {
    expectedVersion: detail.activeGrade?.version ?? 0,
    ...(parsedTotal !== undefined && Number.isFinite(parsedTotal) ? { points: parsedTotal } : {}),
    ...(criteria ? { criteria } : {}),
    comment: values.comment.trim(),
  };
}

export function toFinalizeRequest(
  detail: GradingItemDetailResponse,
  values: GradingItemFormValues,
): FinalizeEssayRequest {
  const rubric = detail.rubric ?? [];
  const existingCriteria = detail.activeGrade?.criteria ?? [];

  const criteria =
    rubric.length > 0
      ? rubric.map((criterion, index) => {
          const parsed = parseScore(values.criteria[index] ?? "");
          const existingComment = existingCriteria.find((entry) => entry.id === criterion.id)?.comment;
          return {
            ...(criterion.id ? { id: criterion.id } : {}),
            ...(Number.isFinite(parsed) ? { points: parsed } : {}),
            ...(existingComment ? { comment: existingComment } : {}),
          };
        })
      : undefined;

  return {
    expectedVersion: detail.activeGrade?.version ?? 0,
    points: parseScore(values.totalPoints),
    ...(criteria ? { criteria } : {}),
    comment: values.comment.trim(),
  };
}
