import type { AttemptPreChoiceSnapshot } from "./db";
import { loadAttemptMeta, saveAttemptMeta } from "./draft-store";
import {
  ATTEMPT_START_KEY_PREFIX,
  safeStorageGet,
  safeStorageRemove,
  safeStorageSet,
} from "./session-storage";

export interface AttemptStartContext {
  entitlementId: string;
  preChoice?: AttemptPreChoiceSnapshot | null;
  examName?: string;
  examKind?: string;
  firstPartName?: string;
  firstPartDurationMinutes?: number | null;
}

export function makeAttemptStartContextKey(userId: string, attemptId: string): string {
  return `${ATTEMPT_START_KEY_PREFIX}${userId}:${attemptId}`;
}

function isValidPreChoice(value: unknown): value is AttemptPreChoiceSnapshot {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  if (typeof record.optionCode !== "string" || !record.optionCode.trim()) return false;
  if (
    record.subjectCodes !== undefined &&
    (!Array.isArray(record.subjectCodes) || record.subjectCodes.some((s) => typeof s !== "string"))
  ) {
    return false;
  }
  return true;
}

function parseStartContext(raw: string | null): AttemptStartContext | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null) return null;
    const record = parsed as Record<string, unknown>;
    if (typeof record.entitlementId !== "string" || !record.entitlementId.trim()) return null;
    return {
      entitlementId: record.entitlementId,
      preChoice: isValidPreChoice(record.preChoice) ? record.preChoice : undefined,
      examName: typeof record.examName === "string" ? record.examName : undefined,
      examKind: typeof record.examKind === "string" ? record.examKind : undefined,
      firstPartName: typeof record.firstPartName === "string" ? record.firstPartName : undefined,
      firstPartDurationMinutes:
        typeof record.firstPartDurationMinutes === "number" || record.firstPartDurationMinutes === null
          ? record.firstPartDurationMinutes
          : undefined,
    };
  } catch {
    return null;
  }
}

export function readAttemptStartContext(userId: string, attemptId: string): AttemptStartContext | null {
  if (!userId || !attemptId) return null;
  return parseStartContext(safeStorageGet("session", makeAttemptStartContextKey(userId, attemptId)));
}

export async function loadAttemptStartContext(
  userId: string,
  attemptId: string,
): Promise<AttemptStartContext | null> {
  const fromSession = readAttemptStartContext(userId, attemptId);
  if (fromSession) return fromSession;
  const meta = await loadAttemptMeta(userId, attemptId);
  if (!meta?.entitlementId) return null;
  return {
    entitlementId: meta.entitlementId,
    preChoice: meta.preChoice,
    examName: meta.examName,
    examKind: meta.examKindLabel,
    firstPartName: meta.firstPartName,
    firstPartDurationMinutes: meta.firstPartDurationMinutes,
  };
}

export async function saveAttemptStartContext(
  userId: string,
  attemptId: string,
  context: AttemptStartContext,
): Promise<void> {
  if (!userId || !attemptId || !context.entitlementId) return;
  try {
    safeStorageSet("session", makeAttemptStartContextKey(userId, attemptId), JSON.stringify(context));
  } catch {
    // Ignore serialization/storage errors.
  }
  await saveAttemptMeta({
    userId,
    attemptId,
    entitlementId: context.entitlementId,
    preChoice: context.preChoice ?? undefined,
    examName: context.examName,
    examKindLabel: context.examKind,
    firstPartName: context.firstPartName,
    firstPartDurationMinutes: context.firstPartDurationMinutes,
  });
}

export function clearAttemptStartContext(userId: string, attemptId: string): void {
  if (!userId || !attemptId) return;
  safeStorageRemove("session", makeAttemptStartContextKey(userId, attemptId));
}
