import type { APIRequestContext } from "@playwright/test";
import { setTimeout as delay } from "node:timers/promises";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export interface PublishedExam {
  code: string;
  productCode: string;
  name: string;
  kind: string;
  scope: string;
  partCode: string | null;
  subjectCode: string | null;
  template: string;
  buildMode: string;
  durationMinutes: number | null;
  size: string;
  purpose: string;
  miniCode: string | null;
  miniOf?: string;
  listing: {
    priceVnd: number;
    attemptsGranted: number;
    resultView: string;
  };
}

export interface PublishedBundle {
  code: string;
  name: string;
  summary: string;
  description: string;
  kind: string;
  examCodes: string[];
  priceVnd: number;
  attemptsPerExam: number;
  resultView: string;
}

export interface PublishedExamSet {
  version: number;
  note: string;
  actor: string;
  accounts: Array<{ username: string; role: string }>;
  discontinuedExam: string;
  voucherExam: string;
  scoreOnlyExam: string;
  reviewExam: string;
  imageQuestion: {
    examCode: string;
    questionCode: string;
    assets: Array<{ role: string; fileName: string }>;
  };
  exams: PublishedExam[];
  sp2eBundle?: PublishedBundle;
}

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, "../../..");
const EXAM_SET_PATH = resolve(REPO_ROOT, "backend/src/main/resources/devdata/published-exam-set.json");

export function publishedExamSet(): PublishedExamSet {
  return JSON.parse(readFileSync(EXAM_SET_PATH, "utf8")) as PublishedExamSet;
}

export function allExams(): PublishedExam[] {
  return publishedExamSet().exams;
}

export function examByCode(code: string): PublishedExam {
  const exam = allExams().find((item) => item.code === code);
  if (!exam) throw new Error(`Published exam not found in data set: ${code}`);
  return exam;
}

export function miniOf(code: string): PublishedExam | undefined {
  return allExams().find((item) => item.miniOf === code);
}

export function discontinuedExam(): PublishedExam {
  return examByCode(publishedExamSet().discontinuedExam);
}

export function voucherExam(): PublishedExam {
  return examByCode(publishedExamSet().voucherExam);
}

export function scoreOnlyExam(): PublishedExam {
  return examByCode(publishedExamSet().scoreOnlyExam);
}

export function reviewExam(): PublishedExam {
  return examByCode(publishedExamSet().reviewExam);
}

export function imageQuestion(): PublishedExamSet["imageQuestion"] {
  return publishedExamSet().imageQuestion;
}

export function accounts(): PublishedExamSet["accounts"] {
  return publishedExamSet().accounts;
}

export function sp2eBundle(): PublishedBundle {
  const bundle = publishedExamSet().sp2eBundle;
  if (!bundle) {
    throw new Error("Missing sp2eBundle in published-exam-set.json");
  }
  return bundle;
}

export interface WaitForPublishedExamSetOptions {
  timeoutMs?: number;
  pollIntervalMs?: number;
}

interface ExamListResponse {
  items: Array<{ code: string; status: string }>;
  totalPages?: number;
}

/** Wait until the admin exam list exposes every fixture exam in its expected published state. */
export async function waitForPublishedExamSet(
  request: APIRequestContext,
  options: WaitForPublishedExamSetOptions = {},
): Promise<void> {
  const timeoutMs = options.timeoutMs ?? 240_000;
  const pollIntervalMs = options.pollIntervalMs ?? 1_000;
  const set = publishedExamSet();
  const expected = new Map<string, string>(
    set.exams.map(
      (exam) => [exam.code, exam.code === set.discontinuedExam ? "DISCONTINUED" : "PUBLISHED"] as const,
    ),
  );
  const adminUrl = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
  const deadline = Date.now() + timeoutMs;
  let lastApiError: string | undefined;

  while (true) {
    const found = new Map<string, string>();
    lastApiError = undefined;
    try {
      let page = 0;
      let totalPages = 1;
      while (page < totalPages) {
        const params = new URLSearchParams({ page: String(page), size: "100" });
        const response = await request.get(`${adminUrl}/api/v1/admin/exams?${params}`);
        if (!response.ok()) {
          lastApiError = `${response.status()} ${await response.text()}`;
          break;
        }
        const result = (await response.json()) as ExamListResponse;
        for (const exam of result.items) found.set(exam.code, exam.status);
        totalPages = result.totalPages ?? 1;
        page += 1;
      }
      if (!lastApiError) {
        const unresolved = [...expected].filter(([code, status]) => found.get(code) !== status);
        if (unresolved.length === 0) return;
        if (Date.now() >= deadline) {
          const details = unresolved.map(([code, status]) => `${code} (${found.has(code) ? `status ${found.get(code)}, expected ${status}` : "missing"})`);
          throw new Error(`Timed out waiting for published exam set; missing or unpublished codes: ${details.join(", ")}`);
        }
      }
    } catch (error) {
      if (error instanceof Error && error.message.startsWith("Timed out waiting for published exam set;")) throw error;
      lastApiError = error instanceof Error ? error.message : String(error);
    }

    if (Date.now() >= deadline) {
      const unresolved = [...expected].filter(([code, status]) => found.get(code) !== status);
      const details = unresolved.map(([code, status]) => `${code} (${found.has(code) ? `status ${found.get(code)}, expected ${status}` : "missing"})`);
      const apiError = lastApiError ? ` Last API error: ${lastApiError}.` : "";
      throw new Error(`Timed out waiting for published exam set; missing or unpublished codes: ${details.join(", ")}.${apiError}`);
    }
    await delay(Math.min(pollIntervalMs, deadline - Date.now()));
  }
}
