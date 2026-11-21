export const SCORE_DECIMALS = 2;
export const COMMENT_MAX = 2000;

const SCORE_PATTERN = new RegExp(`^\\d+([.,]\\d{1,${SCORE_DECIMALS}})?$`);

/** Parse decimal score supporting comma or dot ("1,5" or "1.5"); invalid or negative -> NaN. */
export function parseScore(value: string): number {
  const trimmed = value.trim();
  if (!SCORE_PATTERN.test(trimmed)) return Number.NaN;
  return Number(trimmed.replace(",", "."));
}

/** Round to 2 decimal places so decimal additions like 0.1 + 0.2 stay exact. */
export function roundScore(value: number): number {
  return Math.round(value * 10 ** SCORE_DECIMALS) / 10 ** SCORE_DECIMALS;
}

export function sumScores(values: ReadonlyArray<number | null | undefined>): number {
  return roundScore(values.reduce<number>((total, value) => total + (value ?? 0), 0));
}

export type ScoreIssue = "invalid" | "tooHigh" | "required";

/** Validate a score input against `[0, max]`. Empty is allowed unless `required` is true. */
export function checkScoreInput(value: string, max: number, required: boolean): ScoreIssue | null {
  if (value.trim() === "") return required ? "required" : null;
  const score = parseScore(value);
  if (!Number.isFinite(score)) return "invalid";
  if (score > max) return "tooHigh";
  return null;
}

/** Format numeric score using the active locale's decimal separator. */
export function formatScoreInput(score: number | null | undefined, decimalSeparator: string): string {
  if (score === null || score === undefined || !Number.isFinite(score)) return "";
  return String(score).replace(".", decimalSeparator);
}

/** Validate `YYYY-MM-DD` calendar date string. */
export function isValidCalendarDate(dateStr: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr.trim());
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const check = new Date(Date.UTC(year, month - 1, day));
  return (
    !Number.isNaN(check.getTime()) &&
    check.getUTCFullYear() === year &&
    check.getUTCMonth() === month - 1 &&
    check.getUTCDate() === day
  );
}
