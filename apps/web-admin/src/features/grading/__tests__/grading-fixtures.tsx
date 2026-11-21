import { renderWithProviders } from "@/test/render";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import type { ReactElement } from "react";
import type {
  EssayGradeView,
  GradingItemDetailResponse,
  GradingQueueCountsResponse,
  GradingQueueItemView,
} from "../types";

export const GRADER_1_ID = "11111111-1111-4111-8111-111111111111";
export const GRADER_2_ID = "22222222-2222-4222-8222-222222222222";
export const EXAM_1_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
export const ATTEMPT_1_ID = "abcdef12-3456-4789-9abc-def123456789";
export const ITEM_1_ID = "99999999-9999-4999-8999-999999999999";

export const EXAM_KIND_CATALOG_FIXTURES = [
  {
    id: "k-bca",
    code: "BCA",
    nameVi: "Danh gia tuyen sinh Cong an (BCA)",
    nameEn: "Public Security Admission (BCA)",
    displayOrder: 1,
    value: 3,
  },
  {
    id: "k-spt",
    code: "SPT",
    nameVi: "Danh gia nang luc DH Su pham (SPT)",
    nameEn: "",
    displayOrder: 2,
    value: 5,
  },
];

export function makeSession(overrides: Partial<Session> = {}): Session {
  return {
    ...TEST_ADMIN_SESSION,
    id: GRADER_1_ID,
    username: "grader.one",
    fullName: "Grader One",
    permissions: ["GRADING.VIEW", "GRADING.MODIFY"],
    ...overrides,
  };
}

export function renderGradingUi(
  ui: ReactElement,
  options: { locale?: "vi" | "en"; session?: Session } = {},
) {
  const locale = options.locale ?? "vi";
  const session = options.session ?? makeSession();
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>{ui}</SessionProvider>,
    {
      locale,
      messages: locale === "en" ? en : viMessages,
    },
  );
}

export function makeQueueItem(
  overrides: Partial<GradingQueueItemView> = {},
): GradingQueueItemView {
  return {
    itemId: ITEM_1_ID,
    attemptId: ATTEMPT_1_ID,
    examId: EXAM_1_ID,
    examName: "BCA Literature Mock 01",
    examKindCode: "BCA",
    partCode: "P3",
    partName: "Writing Section",
    displayNo: 25,
    pointsMax: 25,
    submittedAt: "2026-11-20T03:00:00Z",
    state: "WAITING",
    regrading: false,
    ...overrides,
  };
}

export function makeCounts(
  overrides: Partial<GradingQueueCountsResponse> = {},
): GradingQueueCountsResponse {
  return {
    waiting: 3,
    inProgress: 2,
    done: 1,
    total: 6,
    ...overrides,
  };
}

export function makeEssayGrade(overrides: Partial<EssayGradeView> = {}): EssayGradeView {
  return {
    id: "grade-1",
    attemptItemId: ITEM_1_ID,
    state: "DRAFT",
    graderId: GRADER_1_ID,
    graderName: "Grader One",
    claimedAt: "2026-11-20T03:05:00Z",
    lastActivityAt: "2026-11-20T03:10:00Z",
    claimExpired: false,
    points: undefined,
    criteria: [],
    comment: "",
    version: 1,
    ...overrides,
  };
}

export function makeDetail(
  overrides: Partial<GradingItemDetailResponse> = {},
): GradingItemDetailResponse {
  return {
    itemId: ITEM_1_ID,
    attemptId: ATTEMPT_1_ID,
    examId: EXAM_1_ID,
    examName: "BCA Literature Mock 01",
    examKindCode: "BCA",
    partCode: "P3",
    partName: "Writing Section",
    displayNo: 25,
    questionId: "q-essay-1",
    questionVersionNo: 2,
    pointsMax: 10,
    submittedAt: "2026-11-20T03:00:00Z",
    state: "WAITING",
    regrading: false,
    content: "<p>Analyze the passage and present your argument.</p>",
    passage: "<p>Reading passage excerpt for essay prompt.</p>",
    answerHtml: "<p>Student essay answer with clear structure and examples.</p>",
    wordLimit: 5,
    wordCount: 9,
    gradingGuide: "<p>Check thesis, evidence, and coherence.</p>",
    rubric: [
      { id: "c1", description: "Structure and thesis", points: 4 },
      { id: "c2", description: "Evidence and analysis", points: 6 },
    ],
    sampleAnswer: "<p>Suggested sample response outline.</p>",
    activeGrade: undefined,
    history: [],
    ...overrides,
  };
}
