import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

/**
 * UNI-2752 (AC-3, AC-4): bước 2 của bài "Tự thiết lập" (mẫu bài ôn). Chỗ sửa của khối duy nhất là màn bài ôn, không phải
 * màn nhóm đầy đủ; ba giá trị của bước 1 chỉ đọc, các tùy chọn còn lại nằm trong một vùng đóng, không bắt buộc.
 */

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const STRUCTURE = `${EXAM_PATH}/structure`;
const CHECK = `${EXAM_PATH}/check`;
const OVERVIEW_HREF = `/exams/new?exam=${EXAM_ID}`;

const reviewTemplate = {
  code: "T_REVIEW",
  kindCode: "HSA",
  scope: "FULL",
  timing: "PER_PART",
  label: "Bài ôn",
  active: true,
  isDefault: true,
  buildMode: "REVIEW",
  publishable: true,
  fixedCounts: false,
  structure: { parts: [] },
};

/** Lỗi kiểm tra gắn với nhóm duy nhất của bài ôn (dạng máy chủ trả trước khi được nới: nhóm chưa có dạng trả lời). */
const blockIssue = {
  key: "GROUP_ANSWER_TYPE_MISSING|blk-1",
  code: "GROUP_ANSWER_TYPE_MISSING",
  severity: "ERROR",
  group: "STRUCTURE",
  partId: "part-1",
  sectionId: "sec-1",
  subjectCode: "MATH",
  blockId: "blk-1",
  blockName: "Choice",
};

const failedCheck = {
  examId: EXAM_ID,
  status: "INVALID",
  stale: false,
  checkedAt: "2026-11-17T01:00:00Z",
  durationSeconds: 3,
  timeLimitSeconds: 30,
  issueCount: 1,
  partCount: 1,
  totalPoints: 0,
  printedQuestions: 10,
  paths: [],
  groups: [{ group: "STRUCTURE", status: "FAIL", issues: [blockIssue] }],
};

/** Bài ôn đúng dạng máy chủ trả sau bước 1: phần 10 câu, một môn, một nhóm chưa khai số câu, dạng "Bất kỳ". */
function boot(exam: Parameters<typeof examDetailWire>[0] = {}) {
  const base = FIXED_PART.sections![0] as object;
  const part = { ...FIXED_PART, durationMinutes: 15, sections: [{ ...base, blocks: [{ id: "blk-1", questionType: null, questionCount: null }] }] };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, maxScore: 10, templateCode: "T_REVIEW", buildMode: "REVIEW", ...exam }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [reviewTemplate] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .on("POST", CHECK, { status: 200, body: failedCheck })
    .install();
}

function open(search: string) {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

const reviewHeading = () => screen.findByRole("heading", { name: /^Bài ôn ·/ });

beforeEach(() => {
  router.push.mockReset();
  window.history.replaceState(null, "", "/");
});

describe("Bài ôn: chỗ sửa của khối duy nhất là màn bài ôn (AC-3)", () => {
  it("?block= của khối duy nhất mở màn bài ôn, không mở màn nhóm đầy đủ", async () => {
    boot();
    open("?block=blk-1");
    expect(await reviewHeading()).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Tên nhóm/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Về màn môn" })).not.toBeInTheDocument();
  });

  it("'Tiếp tục' gặp lỗi của nhóm: dẫn tới màn bài ôn, không phải màn nhóm", async () => {
    boot();
    const user = userEvent.setup();
    open("");
    await user.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(screen.getByTestId("structure-check-result")).toBeInTheDocument());
    expect(router.push).not.toHaveBeenCalled();
    expect(await reviewHeading()).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Tên nhóm/ })).not.toBeInTheDocument();
  });

  it("'Đến chỗ sửa' của lỗi nhóm trong kết quả kiểm tra mở màn bài ôn", async () => {
    boot();
    const user = userEvent.setup();
    open("?part=part-1");
    await user.click(await screen.findByRole("button", { name: "Kiểm tra toàn bài" }));
    const result = await screen.findByTestId("structure-check-result");
    await user.click(within(result).getByRole("button", { name: "Đến chỗ sửa" }));
    expect(await reviewHeading()).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Tên nhóm/ })).not.toBeInTheDocument();
  });
});

describe("Bài ôn: ba giá trị của bước 1 và vùng tùy chọn (AC-4)", () => {
  it("số câu, thời lượng và thang điểm chỉ đọc, kèm đường dẫn Sửa thông tin tổng quan về tổng quan", async () => {
    boot();
    open("?section=sec-1");
    await reviewHeading();
    for (const [name, value] of [
      [/^Số lượng/, "10"],
      [/^Thời lượng/, "15"],
      [/^Thang điểm/, "10"],
    ] as const) {
      const field = screen.getByRole("textbox", { name });
      expect(field).toHaveValue(value);
      expect(field).toHaveAttribute("readonly");
      expect(field).toHaveAttribute("aria-readonly", "true");
    }
    const link = within(screen.getByRole("textbox", { name: /^Số lượng/ }).closest("section")!).getByRole("link", { name: "Sửa thông tin tổng quan" });
    expect(link).toHaveAttribute("href", OVERVIEW_HREF);
  });

  it("không có thang điểm ở bước 1 thì không hiện ô Thang điểm", async () => {
    boot({ maxScore: undefined });
    open("?section=sec-1");
    await reviewHeading();
    expect(screen.getByRole("textbox", { name: /^Thời lượng/ })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Thang điểm/ })).not.toBeInTheDocument();
  });

  it("phạm vi kiến thức, dạng câu, phân bố và câu chùm nằm trong một vùng đóng 'Tùy chọn thêm (không bắt buộc)'", async () => {
    boot();
    open("?section=sec-1");
    await reviewHeading();
    const optional = screen.getByText("Tùy chọn thêm (không bắt buộc)").closest("details")!;
    expect(optional).not.toHaveAttribute("open");
    expect(within(optional).getByText("Phạm vi kiến thức")).toBeInTheDocument();
    expect(within(optional).getByRole("combobox", { name: "Dạng câu" })).toHaveTextContent("Bất kỳ");
    expect(within(optional).getByText("Phân bố khối lớp và độ khó")).toBeInTheDocument();
    expect(within(optional).getByText("Câu chùm")).toBeInTheDocument();
    // Ba giá trị của bước 1 nằm ngoài vùng tùy chọn.
    expect(within(optional).queryByRole("textbox", { name: /^Số lượng/ })).not.toBeInTheDocument();
  });
});
