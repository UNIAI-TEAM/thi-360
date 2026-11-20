import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { PublishExamDialog } from "../components/publish-exam-dialog";
import type { ExamVersionList } from "../review-types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex/versions",
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const PUBLISH_PATH = `${EXAM_PATH}/publish`;
const DEFAULT_PATH = { pathId: "default", choices: [] };

const VERSIONS: ExamVersionList = {
  examId: EXAM_ID,
  workingCopy: {
    editedBy: { id: "u1", name: "Cô Lan" },
    savedAt: "2026-11-17T02:00:00Z",
    baseVersion: null,
    note: null,
    check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
    unpublishedPaths: [DEFAULT_PATH],
  },
  changes: [],
  items: [],
};

let server: FakeServer;

beforeEach(() => {
  server = new FakeServer()
    .on("GET", `${EXAM_PATH}/check`, { status: 200, body: checkResultWire("VALID") })
    .on("POST", PUBLISH_PATH, {
      status: 409,
      body: { code: "CHECK_REQUIRED", message: "Check required", fieldErrors: [{ field: "reason", code: "RULES_CHANGED" }] },
    })
    .install();
});

function renderDialog(locale?: "en") {
  const exam = mapExamDetail(examDetailWire({ check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" } }));
  return renderWithProviders(
    <PublishExamDialog open onOpenChange={() => {}} exam={exam} versions={VERSIONS} onPublished={() => {}} />,
    locale ? { locale, messages: en } : undefined,
  );
}

describe("rulesChangedLeadsToRecheck", () => {
  it("vi: 409 CHECK_REQUIRED lý do RULES_CHANGED hiện nhãn riêng, không rơi về câu chung, dẫn tới trang Kiểm tra", async () => {
    const user = userEvent.setup();
    renderDialog();
    const dialog = await screen.findByRole("dialog");

    await user.click(await within(dialog).findByRole("button", { name: "Công bố 1 đường lựa chọn" }));

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent(
      "Quy tắc kiểm tra đã được cập nhật sau lần kiểm tra gần nhất. Kiểm tra lại cả bài rồi công bố.",
    );
    expect(alert).not.toHaveTextContent("Cấu hình cần được kiểm tra lại trước khi công bố.");
    expect(alert).not.toHaveTextContent("Check required");
    expect(within(alert).getByRole("link", { name: "Kiểm tra lại" })).toHaveAttribute("href", `/exams/${EXAM_ID}/review`);
    expect(server.calls("POST", PUBLISH_PATH)).toHaveLength(1);
  });

  it("en: cùng lý do hiện câu tiếng Anh và liên kết Check again", async () => {
    const user = userEvent.setup();
    renderDialog("en");
    const dialog = await screen.findByRole("dialog");

    await user.click(await within(dialog).findByRole("button", { name: "Publish 1 choice path" }));

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent(
      "The check rules were updated after the latest check. Check the whole exam again, then publish.",
    );
    expect(alert).not.toHaveTextContent("The configuration must be checked again before publishing.");
    expect(within(alert).getByRole("link", { name: "Check again" })).toHaveAttribute("href", `/exams/${EXAM_ID}/review`);
  });
});
