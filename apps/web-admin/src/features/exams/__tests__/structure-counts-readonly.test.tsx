import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

/**
 * UNI-2753 (AC-5, AC-6): ở bước 2 mọi ô sửa số câu (phần, mục, bài ôn) chỉ đọc cho mọi bài; số câu chỉ sửa ở thông tin tổng quan.
 * Số câu của nhóm, phương án phải chọn và ma trận vẫn sửa được.
 */

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;
const OVERVIEW_HREF = `/exams/new?exam=${EXAM_ID}`;
const LINK = "Sửa thông tin tổng quan";

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

const block = (id: string, questionCount: number) => ({ id, name: id, questionType: "SINGLE_CHOICE", questionCount, requiredPointsPerQuestion: 1 });
const section = (id: string, subjectCode: string, questionCount: number) => ({ id, subjectCode, questionCount, blocks: [block(`blk-${id}`, questionCount)] });

/** Phần cố định hai môn (6 + 4 câu), bài thường không khóa số câu theo mẫu. */
function bootStandard() {
  const part = { ...FIXED_PART, questionCount: 10, sections: [section("sec-1", "MATH", 6), section("sec-2", "PHYSICS", 4)] };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
    .install();
}

function bootReview() {
  const part = { ...FIXED_PART, questionCount: 10, sections: [section("sec-1", "MATH", 10)] };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, templateCode: "T_REVIEW", buildMode: "REVIEW" }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [reviewTemplate] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
    .install();
}

function open(search: string) {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

/** Ô chỉ đọc kèm đường dẫn "Sửa thông tin tổng quan" ngay cạnh, về tab thông tin tổng quan của chính bài này. */
function expectReadOnlyWithLink(field: HTMLElement) {
  expect(field).toHaveAttribute("readonly");
  expect(field).toHaveAttribute("aria-readonly", "true");
  const link = within(field.parentElement!).getByRole("link", { name: LINK });
  expect(link).toHaveAttribute("href", OVERVIEW_HREF);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Bước 2, bài thường: số câu chỉ đọc", () => {
  it("phần: Số câu phải làm và Số câu trên đề chỉ đọc, mỗi ô có đường dẫn sửa ở tổng quan", async () => {
    bootStandard();
    open("?part=part-1");
    expectReadOnlyWithLink(await screen.findByRole("textbox", { name: /^Số câu phải làm/ }));
    expectReadOnlyWithLink(screen.getByRole("textbox", { name: /^Số câu trên đề/ }));
  });

  it("bảng môn của phần: số câu từng môn là chữ, không phải ô nhập; đường dẫn sửa ở đầu bảng", async () => {
    bootStandard();
    open("?part=part-1");
    const table = (await screen.findByRole("heading", { name: "Môn trong phần" })).closest("section")!;
    expect(within(table).queryByRole("textbox", { name: /^Số câu của môn/ })).not.toBeInTheDocument();
    const first = within(table).getAllByRole("button", { name: /^Soạn môn/ })[0]!.closest("tr")!;
    expect(within(first).getAllByRole("cell")[0]).toHaveTextContent("6");
    expect(within(table).getByRole("link", { name: LINK })).toHaveAttribute("href", OVERVIEW_HREF);
  });

  it("mục (môn): không còn ô Số câu của mục, đầu màn vẫn nêu số câu và Đã phân bổ, đường dẫn đầu màn vẫn về bước 1; số câu của nhóm sửa được", async () => {
    bootStandard();
    const view = open("?section=sec-1");
    const title = await screen.findByRole("heading", { name: /^Môn / });
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
    expect(screen.queryByText("Số câu của mục")).not.toBeInTheDocument();
    expect(screen.queryByText(/Số câu lấy từ thông tin tổng quan/)).not.toBeInTheDocument();
    // Tiêu đề màn môn và dòng số câu bên dưới, "Đã phân bổ a / N câu" ở bên phải.
    const header = title.closest("div")!.parentElement!;
    expect(within(header).getByText("6 câu")).toBeInTheDocument();
    expect(within(header).getByText(/^Đã phân bổ/)).toBeInTheDocument();
    // Đường dẫn "Sửa thông tin tổng quan" ở đầu màn Cấu trúc vẫn mở bước 1; trong màn môn không còn bản thứ hai.
    const summary = screen.getByLabelText("Tổng hợp cấu trúc");
    expect(within(summary.parentElement!).getByRole("link", { name: LINK })).toHaveAttribute("href", OVERVIEW_HREF);
    expect(screen.getAllByRole("link", { name: LINK })).toHaveLength(1);
    view.unmount();

    open("?block=blk-sec-1");
    expect(await screen.findByRole("textbox", { name: /^Số câu cần lấy/ })).not.toHaveAttribute("readonly");
  });
});

describe("Bước 2, bài ôn: Số lượng chỉ đọc", () => {
  it("Số lượng chỉ đọc kèm đường dẫn sửa ở tổng quan", async () => {
    bootReview();
    open("?section=sec-1");
    expectReadOnlyWithLink(await screen.findByRole("textbox", { name: /^Số lượng/ }));
  });
});
