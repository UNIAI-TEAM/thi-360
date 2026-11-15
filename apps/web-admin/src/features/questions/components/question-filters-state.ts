"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useMemo, useRef, useState } from "react";
import {
  QUESTION_STATUSES,
  QUESTION_TYPES,
  type QuestionStatus,
  type QuestionType,
} from "@/features/content/types";
import type { ListQuestionsQuery } from "../types";

/**
 * Bộ lọc của Ngân hàng câu hỏi, giữ trên URL để chia sẻ và tải lại trang vẫn đúng (DESIGN.md mục 5.13: tên và giá
 * trị query bằng tiếng Anh). Tham số khác (ví dụ `mockState`) được giữ nguyên.
 */

export type SolutionFilter = "has" | "none";

export interface QuestionFilters {
  q: string;
  status: QuestionStatus | null;
  subject: string | null;
  grade: string | null;
  difficulty: string | null;
  type: QuestionType | null;
  /** Id nút kiến thức (MAPID); Chương hoặc Bài thì gồm cả kiến thức bên dưới. */
  knowledge: string[];
  tag: string | null;
  author: string | null;
  solution: SolutionFilter | null;
  /** Ngày tạo `YYYY-MM-DD`. */
  createdFrom: string | null;
  createdTo: string | null;
  /** Trang tính từ 1. */
  page: number;
  size: number;
  /** `field,asc|desc`; `null` là mặc định (mới cập nhật trước). */
  sort: string | null;
}

export const QUESTION_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_QUESTION_PAGE_SIZE = 20;
export const SORTABLE_QUESTION_FIELDS = ["code", "points", "status", "updatedAt"] as const;

export const DEFAULT_QUESTION_FILTERS: QuestionFilters = {
  q: "",
  status: null,
  subject: null,
  grade: null,
  difficulty: null,
  type: null,
  knowledge: [],
  tag: null,
  author: null,
  solution: null,
  createdFrom: null,
  createdTo: null,
  page: 1,
  size: DEFAULT_QUESTION_PAGE_SIZE,
  sort: null,
};

const PARAM = {
  q: "q",
  status: "status",
  subject: "subject",
  grade: "grade",
  difficulty: "difficulty",
  type: "type",
  knowledge: "knowledge",
  tag: "tag",
  author: "author",
  solution: "solution",
  createdFrom: "createdFrom",
  createdTo: "createdTo",
  page: "page",
  size: "size",
  sort: "sort",
} as const satisfies Record<keyof QuestionFilters, string>;

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function oneOf<T extends string>(values: readonly T[], value: string | null): T | null {
  return value !== null && (values as readonly string[]).includes(value) ? (value as T) : null;
}

function text(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function parseQuestionFilters(search: string): QuestionFilters {
  const params = new URLSearchParams(search);
  const page = Number.parseInt(params.get(PARAM.page) ?? "", 10);
  const size = Number.parseInt(params.get(PARAM.size) ?? "", 10);
  const sort = params.get(PARAM.sort);
  const [sortField, sortDirection] = sort?.split(",") ?? [];
  const validSort =
    sortField !== undefined &&
    (SORTABLE_QUESTION_FIELDS as readonly string[]).includes(sortField) &&
    (sortDirection === "asc" || sortDirection === "desc");
  const date = (value: string | null) => (value && DATE_RE.test(value) ? value : null);
  return {
    q: params.get(PARAM.q)?.trim() ?? "",
    status: oneOf(QUESTION_STATUSES, params.get(PARAM.status)),
    subject: text(params.get(PARAM.subject)),
    grade: text(params.get(PARAM.grade)),
    difficulty: text(params.get(PARAM.difficulty)),
    type: oneOf(QUESTION_TYPES, params.get(PARAM.type)),
    knowledge: (params.get(PARAM.knowledge) ?? "")
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean),
    tag: text(params.get(PARAM.tag)),
    author: text(params.get(PARAM.author)),
    solution: oneOf(["has", "none"] as const, params.get(PARAM.solution)),
    createdFrom: date(params.get(PARAM.createdFrom)),
    createdTo: date(params.get(PARAM.createdTo)),
    page: Number.isFinite(page) && page > 0 ? page : 1,
    size: (QUESTION_PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_QUESTION_PAGE_SIZE,
    sort: validSort ? `${sortField},${sortDirection}` : null,
  };
}

/** Ghi bộ lọc vào chuỗi query, bỏ giá trị mặc định và giữ các tham số không thuộc bộ lọc. */
export function serializeQuestionFilters(filters: QuestionFilters, baseSearch = ""): string {
  const params = new URLSearchParams(baseSearch);
  for (const name of Object.values(PARAM)) params.delete(name);
  const set = (name: string, value: string | null) => {
    if (value) params.set(name, value);
  };
  set(PARAM.q, filters.q.trim() || null);
  set(PARAM.status, filters.status);
  set(PARAM.subject, filters.subject);
  set(PARAM.grade, filters.grade);
  set(PARAM.difficulty, filters.difficulty);
  set(PARAM.type, filters.type);
  set(PARAM.knowledge, filters.knowledge.length ? filters.knowledge.join(",") : null);
  set(PARAM.tag, filters.tag);
  set(PARAM.author, filters.author);
  set(PARAM.solution, filters.solution);
  set(PARAM.createdFrom, filters.createdFrom);
  set(PARAM.createdTo, filters.createdTo);
  set(PARAM.page, filters.page > 1 ? String(filters.page) : null);
  set(PARAM.size, filters.size !== DEFAULT_QUESTION_PAGE_SIZE ? String(filters.size) : null);
  set(PARAM.sort, filters.sort);
  return params.toString();
}

/** Tham số gọi API (trang tính từ 0). */
export function toListQuestionsQuery(filters: QuestionFilters): ListQuestionsQuery {
  return {
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.subject ? { subjectCode: filters.subject } : {}),
    ...(filters.grade ? { gradeCode: filters.grade } : {}),
    ...(filters.difficulty ? { difficultyCode: filters.difficulty } : {}),
    ...(filters.type ? { type: filters.type } : {}),
    ...(filters.knowledge.length ? { knowledgeIds: filters.knowledge } : {}),
    ...(filters.tag ? { tagCode: filters.tag } : {}),
    ...(filters.author ? { createdById: filters.author } : {}),
    ...(filters.solution ? { hasSolution: filters.solution === "has" } : {}),
    ...(filters.createdFrom ? { createdFrom: filters.createdFrom } : {}),
    ...(filters.createdTo ? { createdTo: filters.createdTo } : {}),
    page: filters.page - 1,
    size: filters.size,
    sort: filters.sort ?? "updatedAt,desc",
  };
}

/** Bộ lọc nằm trong "Bộ lọc khác" (đếm để hiện trên nút mở). */
export function countAdvancedFilters(filters: QuestionFilters): number {
  return [
    filters.knowledge.length > 0,
    filters.tag,
    filters.author,
    filters.solution,
    filters.createdFrom,
    filters.createdTo,
  ].filter(Boolean).length;
}

/** Có điều kiện lọc nào khác mặc định (không tính trang, cỡ trang, sắp xếp). */
export function hasActiveQuestionFilters(filters: QuestionFilters): boolean {
  return Boolean(
    filters.q ||
      filters.status ||
      filters.subject ||
      filters.grade ||
      filters.difficulty ||
      filters.type ||
      countAdvancedFilters(filters) > 0,
  );
}

/** Đổi bộ lọc thì về trang 1, trừ khi chính `page` được đổi. */
export function applyQuestionFiltersPatch(current: QuestionFilters, patch: Partial<QuestionFilters>): QuestionFilters {
  const next = { ...current, ...patch };
  if (patch.page === undefined) next.page = 1;
  return next;
}

/**
 * Bộ lọc nằm trong state của React (khởi tạo từ URL), URL chỉ là bản sao ghi bằng History API, như
 * `useCatalogUrlState`: không phụ thuộc điều hướng RSC của Next.
 */
export function useQuestionFiltersUrlState(): [QuestionFilters, (patch: Partial<QuestionFilters>) => void] {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const initialSearch = searchParams.toString();
  const [filters, setFilters] = useState(() => parseQuestionFilters(initialSearch));
  const filtersRef = useRef(filters);

  const update = useCallback(
    (patch: Partial<QuestionFilters>) => {
      const next = applyQuestionFiltersPatch(filtersRef.current, patch);
      filtersRef.current = next;
      setFilters(next);
      if (typeof window !== "undefined") {
        const query = serializeQuestionFilters(next, window.location.search);
        window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
      }
    },
    [pathname],
  );

  return useMemo(() => [filters, update], [filters, update]);
}
