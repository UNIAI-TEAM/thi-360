import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mapExamSummary } from "../api-mapping";
import { ExamListRowActions } from "../components/exam-list-actions";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";
import { renderExamsUi } from "./render-exams-ui";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const DISCONTINUE_PATH = `${EXAM_PATH}/discontinue`;

const exam = mapExamSummary({
  id: EXAM_ID,
  code: "HSA-F-001",
  name: "HSA exam 1",
  kind: "HSA",
  scope: "FULL",
  status: "PUBLISHED",
  revision: 7,
  timing: "PER_PART",
  practiceScoring: "RAW",
  servingVersion: 2,
  hasUnpublishedChanges: false,
  questionCount: 10,
  hasEnglishOption: false,
  combinationCount: 0,
  hasEssay: false,
  attemptCount: 0,
  check: { status: "VALID", issueCount: 0 },
  updatedAt: "2026-11-17T01:00:00Z",
} as Parameters<typeof mapExamSummary>[0]);

beforeEach(() => {
});

describe("ngừng bán từ danh sách, nhánh thật", () => {
  it("bấm lại sau lỗi máy chủ gửi lại cùng Idempotency-Key và cùng revision của dòng danh sách", async () => {
    const server = new FakeServer()
      .on(
        "POST",
        DISCONTINUE_PATH,
        { status: 500, body: { code: "INTERNAL" } },
        { status: 200, body: examDetailWire({ status: "DISCONTINUED", revision: 8 }) },
      )
      .install();
    const user = userEvent.setup();
    renderExamsUi(<ExamListRowActions exam={exam} onAnnounce={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Thao tác khác với bài thi HSA-F-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Ngừng bán" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByText(/Không ngừng bán được bài thi/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => expect(server.calls("POST", DISCONTINUE_PATH)).toHaveLength(2));

    const [first, second] = server.calls("POST", DISCONTINUE_PATH).map((request) => request.headers.get("Idempotency-Key"));
    expect(first).toMatch(/^[0-9a-f-]{36}$/);
    expect(second).toBe(first);
    expect(server.calls("POST", DISCONTINUE_PATH)[0]!.body).toEqual({ expectedRevision: 7 });
  });
});
