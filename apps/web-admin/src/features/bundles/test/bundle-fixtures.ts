import {
  type ComponentType,
  createElement,
  type ReactElement,
  type ReactNode,
  useMemo,
  useSyncExternalStore,
} from "react";

import { SessionProvider } from "@/features/session/session-provider";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

import type {
  BundleCandidateResponse,
  BundleDetailResponse,
  BundleItemView,
  BundleSummaryResponse,
  PageResponseBundleSummaryResponse,
} from "../types";

export const FULL_BUNDLE_PERMISSIONS = [
  "EXAM.VIEW",
  "EXAM.MODIFY",
  "EXAM.PUBLISH",
] as const;

export const KIND_SP2E_ID = "019a0000-0000-7000-8000-000000000011";
export const KIND_SPT_ID = "019a0000-0000-7000-8000-000000000012";
export const BUNDLE_ID_1 = "019a0000-0000-7000-8000-000000000101";
export const EXAM_TOAN_ID = "019a0000-0000-7000-8000-000000000201";
export const EXAM_LY_ID = "019a0000-0000-7000-8000-000000000202";
export const EXAM_HOA_ID = "019a0000-0000-7000-8000-000000000203";
export const EXAM_SPT_TOAN_ID = "019a0000-0000-7000-8000-000000000204";

export const EXAM_KIND_OPTIONS = [
  {
    id: KIND_SP2E_ID,
    code: "SP2E",
    nameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2",
    nameEn: "HNUE2 Assessment",
    displayOrder: 1,
    value: 1,
  },
  {
    id: KIND_SPT_ID,
    code: "SPT",
    nameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội",
    nameEn: "",
    displayOrder: 2,
    value: 2,
  },
];

export function makeCandidate(
  overrides: Partial<BundleCandidateResponse> = {},
): BundleCandidateResponse {
  return {
    examId: EXAM_TOAN_ID,
    code: "SP2E-TOAN-01",
    name: "SP2E Toán – Đề luyện số 1",
    scope: "SUBJECT",
    buildMode: "STANDARD",
    subjectItemId: "sub-toan",
    subjectCode: "TOAN",
    subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
    versionNo: 1,
    questionCount: { min: 40, max: 40 },
    durationMinutes: { min: 90, max: 90 },
    maxPoints: { min: 10, max: 10 },
    listPriceVnd: 149000,
    ...overrides,
  };
}

export function makeBundleItem(
  overrides: Partial<BundleItemView> = {},
): BundleItemView {
  return {
    position: 1,
    examId: EXAM_TOAN_ID,
    code: "SP2E-TOAN-01",
    name: "SP2E Toán – Đề luyện số 1",
    scope: "SUBJECT",
    buildMode: "STANDARD",
    subjectItemId: "sub-toan",
    subjectCode: "TOAN",
    subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
    versionNo: 1,
    questionCount: { min: 40, max: 40 },
    durationMinutes: { min: 90, max: 90 },
    maxPoints: { min: 10, max: 10 },
    saleState: "ON_SALE",
    ...overrides,
  };
}

export function makeBundleSummary(
  overrides: Partial<BundleSummaryResponse> = {},
): BundleSummaryResponse {
  return {
    id: BUNDLE_ID_1,
    code: "BO-SP2E-001",
    name: "Bộ đề SP2E khối A00",
    summary: "Ba đề luyện Toán, Vật lí, Hóa học.",
    examKindItemId: KIND_SP2E_ID,
    examKindCode: "SP2E",
    examKindNameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2",
    examKindNameEn: "HNUE2 Assessment",
    listPriceVnd: 349000,
    attemptsPerExam: 2,
    status: "PUBLISHED",
    saleState: "ON_SALE",
    hasUnpublishedChanges: false,
    servingVersion: 1,
    revision: 1,
    itemCount: 3,
    updatedAt: "2026-11-21T02:12:00Z",
    ...overrides,
  };
}

export function makeBundleListPage(
  items: BundleSummaryResponse[],
  overrides: Partial<PageResponseBundleSummaryResponse> = {},
): PageResponseBundleSummaryResponse {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / 20)),
    ...overrides,
  };
}

export function makeBundleDetail(
  overrides: Partial<BundleDetailResponse> = {},
): BundleDetailResponse {
  return {
    id: BUNDLE_ID_1,
    code: "BO-SP2E-001",
    name: "Bộ đề SP2E khối A00",
    summary: "Ba đề luyện Toán, Vật lí, Hóa học.",
    description: "Luyện đủ ba môn khối A00.",
    examKindItemId: KIND_SP2E_ID,
    examKindCode: "SP2E",
    examKindNameVi: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2",
    examKindNameEn: "HNUE2 Assessment",
    listPriceVnd: 349000,
    attemptsPerExam: 2,
    benefits: {
      aiExplanation: true,
      strengthWeakness: true,
      practice: false,
      note: "Áp dụng cho câu trắc nghiệm",
    },
    resultView: "SCORE_AND_ANSWERS",
    status: "DRAFT",
    saleState: null,
    hasUnpublishedChanges: false,
    servingVersion: null,
    revision: 1,
    items: [
      makeBundleItem({
        position: 1,
        examId: EXAM_TOAN_ID,
        code: "SP2E-TOAN-01",
        name: "SP2E Toán – Đề luyện số 1",
      }),
      makeBundleItem({
        position: 2,
        examId: EXAM_LY_ID,
        code: "SP2E-LY-01",
        name: "SP2E Vật lí – Đề luyện số 1",
        subjectItemId: "sub-ly",
        subjectCode: "LY",
        subjectName: { nameVi: "Vật lí", nameEn: "" },
        questionCount: { min: 28, max: 28 },
        durationMinutes: { min: 60, max: 60 },
      }),
    ],
    createdAt: "2026-11-21T01:00:00Z",
    updatedAt: "2026-11-21T02:12:00Z",
    ...overrides,
  };
}

let mockPathname = "/bundles";
let mockSearch = "";
const mockNavListeners = new Set<() => void>();

export function resetBundleNavigation(pathname = "/bundles", search = "") {
  mockPathname = pathname;
  mockSearch = search.startsWith("?") ? search.slice(1) : search;
  bundleMockReplace.fn = undefined;
  for (const listener of mockNavListeners) listener();
}

function navigate(href: string) {
  const qIndex = href.indexOf("?");
  mockPathname = qIndex === -1 ? href : href.slice(0, qIndex);
  mockSearch = qIndex === -1 ? "" : href.slice(qIndex + 1);
  for (const listener of mockNavListeners) listener();
}

export const bundleMockPush = { fn: (href: string) => navigate(href) };
export const bundleMockReplace: { fn?: (href: string) => void } = {};
export function currentBundleMockSearch(): string {
  return mockSearch;
}

export function createBundleNextNavMock() {
  return {
    usePathname: () =>
      useSyncExternalStore(
        (listener) => {
          mockNavListeners.add(listener);
          return () => mockNavListeners.delete(listener);
        },
        () => mockPathname,
        () => mockPathname,
      ),
    useSearchParams: () => {
      const raw = useSyncExternalStore(
        (listener) => {
          mockNavListeners.add(listener);
          return () => mockNavListeners.delete(listener);
        },
        () => mockSearch,
        () => mockSearch,
      );
      return useMemo(() => new URLSearchParams(raw), [raw]);
    },
    useRouter: () => ({
      push: (href: string) => bundleMockPush.fn(href),
      replace: (href: string) => {
        navigate(href);
        bundleMockReplace.fn?.(href);
      },
      prefetch: () => Promise.resolve(),
      back: () => {},
      forward: () => {},
      refresh: () => {},
    }),
  };
}

export function renderBundlesUi(
  ui: ReactElement,
  options?: { permissions?: readonly string[]; locale?: "vi" | "en" },
) {
  const perms = options?.permissions ? [...options.permissions] : [...FULL_BUNDLE_PERMISSIONS];
  const locale = options?.locale ?? "vi";
  const messages = locale === "en" ? en : vi;
  const SessionWrapper = SessionProvider as ComponentType<{
    loadSession?: () => Promise<typeof TEST_ADMIN_SESSION>;
    children?: ReactNode;
  }>;
  return renderWithProviders(
    createElement(
      SessionWrapper,
      {
        loadSession: async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: perms,
        }),
      },
      ui,
    ),
    { locale, messages },
  );
}
