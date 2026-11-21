import type { AttemptPrepareResponse, ChoiceOptionWithMetrics } from "./api";

export interface SelectedPathMetrics {
  partCount: number;
  questionCount: number | null;
  durationMinutes: number | null;
  maxPoints: number | null;
}

export function resolveSelectedPathMetrics(
  prepare: AttemptPrepareResponse,
  selectedOption?: ChoiceOptionWithMetrics | null,
): SelectedPathMetrics {
  const partCount = prepare.parts.length;
  let questionSum = 0;
  let hasQuestionCount = false;
  let pointsSum = 0;
  let hasMaxPoints = false;
  let durationSum = 0;
  let hasPartDuration = false;

  for (const part of prepare.parts) {
    if (typeof part.questionCount === "number") {
      questionSum += part.questionCount;
      hasQuestionCount = true;
    }
    if (typeof part.maxPoints === "number") {
      pointsSum += part.maxPoints;
      hasMaxPoints = true;
    }
    if (typeof part.durationMinutes === "number") {
      durationSum += part.durationMinutes;
      hasPartDuration = true;
    }
  }

  const fallbackQuestions = hasQuestionCount ? questionSum : null;
  const fallbackPoints = hasMaxPoints ? Math.round(pointsSum * 1000) / 1000 : null;
  const fallbackDuration =
    typeof prepare.durationMinutes === "number"
      ? prepare.durationMinutes
      : hasPartDuration
        ? durationSum
        : null;

  return {
    partCount,
    questionCount:
      typeof selectedOption?.questionCount === "number" ? selectedOption.questionCount : fallbackQuestions,
    durationMinutes:
      typeof selectedOption?.durationMinutes === "number"
        ? selectedOption.durationMinutes
        : fallbackDuration,
    maxPoints:
      typeof selectedOption?.maxPoints === "number" ? selectedOption.maxPoints : fallbackPoints,
  };
}
