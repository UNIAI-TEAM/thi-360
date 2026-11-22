import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import type { ReactElement } from "react";
import type {
  AdminAttemptDetailResponse,
  AdminAttemptEventView,
  AdminAttemptItemView,
  AdminAttemptPartView,
  AdminAttemptSummary,
} from "../types";

export const OPERATOR_ID = "11111111-1111-4111-8111-111111111111";
export const LEARNER_1_ID = "22222222-2222-4222-8222-222222222222";
export const EXAM_1_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
export const ATTEMPT_1_ID = "7c1e4b9a-3456-4789-9abc-def123456789";
export const PART_1_ID = "p1111111-1111-4111-8111-111111111111";
export const PART_2_ID = "p2222222-2222-4222-8222-222222222222";
export const PART_3_ID = "p3333333-3333-4333-8333-333333333333";

export const ATTEMPT_EXAM_KIND_FIXTURES = [
  {
    id: "k-hsa",
    code: "HSA",
    nameVi: "Danh gia nang luc DHQG Ha Noi (HSA)",
    nameEn: "VNU Hanoi Aptitude Test (HSA)",
    displayOrder: 1,
    value: 1,
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

export function makeAttemptSession(overrides: Partial<Session> = {}): Session {
  return {
    ...TEST_ADMIN_SESSION,
    id: OPERATOR_ID,
    username: "ops.thanh",
    fullName: "Ops Thanh",
    permissions: ["ATTEMPT.VIEW", "ATTEMPT.COMPENSATE", "GRADING.MODIFY"],
    ...overrides,
  };
}

export function renderAttemptsUi(
  ui: ReactElement,
  options: { locale?: "vi" | "en"; session?: Session } = {},
) {
  const locale = options.locale ?? "vi";
  const session = options.session ?? makeAttemptSession();
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>{ui}</SessionProvider>,
    {
      locale,
      messages: locale === "en" ? en : viMessages,
    },
  );
}

export function makeAttemptSummary(
  overrides: Partial<AdminAttemptSummary> = {},
): AdminAttemptSummary {
  return {
    attemptId: ATTEMPT_1_ID,
    userId: LEARNER_1_ID,
    username: "nguyenminhanh",
    userFullName: "Nguyen Minh Anh",
    examId: EXAM_1_ID,
    examVersionNo: 2,
    examName: "De thi thu DGNL HSA so 3",
    examKindCode: "HSA",
    scope: "FULL",
    attemptNo: 2,
    state: "IN_PROGRESS",
    regrading: false,
    startedAt: "2026-11-17T02:00:00Z",
    createdAt: "2026-11-17T01:59:55Z",
    compensated: false,
    ...overrides,
  };
}

export function makeAttemptPart(
  overrides: Partial<AdminAttemptPartView> = {},
): AdminAttemptPartView {
  return {
    partId: PART_1_ID,
    partCode: "P1",
    partName: "Phan 1 - Tu duy dinh luong",
    ord: 1,
    state: "CLOSED",
    durationMinutes: 75,
    questionCountRequired: 50,
    maxPoints: 50,
    startedAt: "2026-11-17T02:00:12Z",
    deadlineAt: "2026-11-17T03:15:12Z",
    closedAt: "2026-11-17T03:14:05Z",
    closeReason: "FINISHED",
    generationTries: 1,
    ...overrides,
  };
}

export function makeAttemptEvent(
  overrides: Partial<AdminAttemptEventView> = {},
): AdminAttemptEventView {
  return {
    id: "ev-1",
    type: "STARTED",
    sessionId: "sess1234-0000-4000-8000-000000000001",
    at: "2026-11-17T02:00:12Z",
    data: { attemptNo: 2 },
    ...overrides,
  };
}

export function makeAttemptItem(
  overrides: Partial<AdminAttemptItemView> = {},
): AdminAttemptItemView {
  return {
    itemId: "item-1",
    partId: PART_1_ID,
    ord: 1,
    displayNo: 1,
    unitCount: 1,
    questionId: "q-1",
    questionVersionNo: 1,
    questionType: "SINGLE_CHOICE",
    pointsMax: 1,
    inScope: true,
    answer: { type: "SINGLE_CHOICE", optionId: "opt-c" },
    answerReceivedAt: "2026-11-17T02:03:41Z",
    answerSeq: 4,
    flagged: false,
    ...overrides,
  };
}

export function makeAttemptDetail(
  overrides: Partial<AdminAttemptDetailResponse> = {},
): AdminAttemptDetailResponse {
  return {
    attemptId: ATTEMPT_1_ID,
    userId: LEARNER_1_ID,
    username: "nguyenminhanh",
    userFullName: "Nguyen Minh Anh",
    userEmail: "minhanh@thi360.test",
    userCode: "HV-001",
    examId: EXAM_1_ID,
    examVersionNo: 2,
    examName: "De thi thu DGNL HSA so 3",
    examKindCode: "HSA",
    scope: "FULL",
    timing: "PER_PART",
    navigationMode: "SEQUENTIAL",
    attemptNo: 2,
    state: "IN_PROGRESS",
    regrading: false,
    startedAt: "2026-11-17T02:00:12Z",
    editSessionId: "sessbbbb-0000-4000-8000-000000000002",
    editSessionSeenAt: "2026-11-17T04:22:40Z",
    resultView: "FULL_SOLUTION",
    entitlementId: "ent-1",
    grantId: "grant-1",
    compensated: false,
    createdAt: "2026-11-17T02:00:00Z",
    updatedAt: "2026-11-17T04:22:40Z",
    parts: [
      makeAttemptPart(),
      makeAttemptPart({
        partId: PART_3_ID,
        partCode: "P3",
        partName: "Phan 3 - Khoa hoc hoac Tieng Anh",
        ord: 3,
        state: "GENERATION_FAILED",
        startedAt: undefined,
        deadlineAt: undefined,
        closedAt: undefined,
        closeReason: undefined,
        generationTries: 4,
        generationError: "GENERATION_TIMEOUT",
        choice: { optionCode: "SCIENCE", subjectCodes: ["PHY", "CHEM", "BIO"] },
      }),
    ],
    events: [
      makeAttemptEvent({ id: "ev-1", type: "STARTED", at: "2026-11-17T02:00:12Z" }),
      makeAttemptEvent({
        id: "ev-2",
        type: "PART_OPENED",
        partId: PART_1_ID,
        sessionId: undefined,
        at: "2026-11-17T02:00:13Z",
      }),
      makeAttemptEvent({
        id: "ev-3",
        type: "GENERATION_FAILED",
        partId: PART_3_ID,
        sessionId: undefined,
        at: "2026-11-17T04:22:01Z",
        data: { tries: 4, error: "GENERATION_TIMEOUT" },
      }),
    ],
    items: [
      makeAttemptItem({
        itemId: "item-1",
        partId: PART_1_ID,
        displayNo: 1,
        questionType: "SINGLE_CHOICE",
        answer: { type: "SINGLE_CHOICE", optionId: "opt-c" },
        answerReceivedAt: "2026-11-17T02:03:41Z",
        answerSeq: 4,
      }),
      makeAttemptItem({
        itemId: "item-2",
        partId: PART_1_ID,
        ord: 2,
        displayNo: 2,
        questionType: "SHORT_ANSWER",
        answer: { type: "SHORT_ANSWER", text: "12,5" },
        answerReceivedAt: "2026-11-17T02:06:10Z",
        answerSeq: 9,
      }),
      makeAttemptItem({
        itemId: "item-3",
        partId: PART_3_ID,
        ord: 3,
        displayNo: 3,
        questionType: "TRUE_FALSE",
        answer: { type: "TRUE_FALSE", statements: { a: true, b: false } },
        answerReceivedAt: "2026-11-17T02:10:00Z",
        answerSeq: 12,
      }),
    ],
    ...overrides,
  };
}
