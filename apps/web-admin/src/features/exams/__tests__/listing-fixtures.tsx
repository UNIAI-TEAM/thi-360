import type { components } from "@thi360/api-client";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { ExamListingView } from "../listing/listing-tab";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

export const LISTING_PATH = `${EXAM_PATH}/listing`;
export const COVER_PATH = `${EXAM_PATH}/cover`;
export const COVER_ASSET = "33333333-3333-3333-3333-333333333333";
export const NEW_COVER_ASSET = "44444444-4444-4444-4444-444444444444";
export const COVER_URL = `${COVER_PATH}/${COVER_ASSET}`;

type ListingWire = components["schemas"]["ExamListing"];

/** Thông tin bán của bài HSA exam 1 (`revision` 7, như `examDetailWire`): đủ điều kiện công bố, chưa có bản đang bán. */
export function listingWire(overrides: Partial<ListingWire> = {}): ListingWire {
  return {
    examId: EXAM_ID,
    revision: 7,
    hasUnpublishedChanges: true,
    updatedAt: "2026-11-17T02:42:00Z",
    coverAssetId: COVER_ASSET,
    coverUrl: COVER_URL,
    summary: "Short description of the card",
    description: "Exam description shown on the detail page.",
    objectives: "Get used to the time pressure.",
    audience: "Grade 12 students.",
    listPriceVnd: 249000,
    attemptsGranted: 3,
    benefitAiExplanation: true,
    benefitStrengthWeakness: true,
    benefitPractice: false,
    benefitNote: "",
    resultView: "SCORE_AND_ANSWERS",
    minPriceVnd: 12000,
    checkIssues: [],
    ...overrides,
  };
}

/** Bản đang bán `v2` giống hệt bản làm việc của `listingWire()` trừ các trường ghi đè. */
export function publishedWire(overrides: Partial<NonNullable<ListingWire["published"]>["listing"]> = {}): ListingWire["published"] {
  const base = listingWire();
  return {
    versionNo: 2,
    publishedAt: "2026-11-16T03:00:00Z",
    listing: {
      name: "HSA exam 1",
      coverAssetId: base.coverAssetId,
      coverUrl: base.coverUrl,
      summary: base.summary,
      description: base.description,
      objectives: base.objectives,
      audience: base.audience,
      listPriceVnd: base.listPriceVnd,
      attemptsGranted: base.attemptsGranted,
      benefitAiExplanation: base.benefitAiExplanation,
      benefitStrengthWeakness: base.benefitStrengthWeakness,
      benefitPractice: base.benefitPractice,
      benefitNote: base.benefitNote,
      resultView: base.resultView,
      ...overrides,
    },
  };
}

/** Máy chủ giả chỉ có bài thi; tab tự đăng ký tuyến `GET listing` (tuyến đăng ký trước thắng, nên không đăng ký hai lần). */
export function examServer(): FakeServer {
  return new FakeServer().on("GET", EXAM_PATH, { status: 200, body: examDetailWire() });
}

/** Máy chủ giả có bài thi và thông tin bán; thêm tuyến khác (PUT, POST ảnh) sau khi gọi. */
export function listingServer(listing: ListingWire = listingWire()): FakeServer {
  return examServer().on("GET", LISTING_PATH, { status: 200, body: listing });
}

export const VIEW_ONLY = ["EXAM.VIEW"];
export const CAN_EDIT = ["EXAM.VIEW", "EXAM.MODIFY"];

/** Tab Thông tin bán với quyền chọn; mặc định có quyền sửa. */
export function renderListingTab({ permissions = CAN_EDIT }: { permissions?: string[] } = {}) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions })}>
      <ExamListingView examId={EXAM_ID} />
    </SessionProvider>,
  );
}
