import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamReviewView } from "../components/exam-review-view";
import { ExamVersionsView } from "../components/exam-versions-view";
import { ExamsBackend } from "./fake-exams-backend";
import { BCA_RUN, HSA_001_CHECK, PREVIEW_BCA_CA1, THPTQG_TIN_RUN } from "./review-fixtures";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const navigation = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex-bca-f-001/review",
  useSearchParams: () => new URLSearchParams(navigation.search),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const BCA = "ex-bca-f-001";
const THPTQG = "ex-thptqg-tin-001";

let backend: ExamsBackend;

beforeEach(() => {
  navigation.search = "";
  window.history.replaceState(null, "", "/");
  backend = new ExamsBackend();
  // Máy chủ tính kết quả kiểm tra khi bấm "Kiểm tra cấu hình" và sinh đề khi bấm "Sinh đề xem trước".
  backend
    .seedRunResult(THPTQG, THPTQG_TIN_RUN)
    .seedRunResult(BCA, BCA_RUN)
    .seedCheck("ex-hsa-f-001", HSA_001_CHECK)
    .seedPreview(BCA, "opt-b3-1", { status: 200, body: PREVIEW_BCA_CA1 });
  backend.install();
});

describe("đề xem trước theo bảy kỳ thi", () => {
  it("màn Đề xem trước của BCA hiện điểm phần và nhãn Chấm tay", async () => {
    const user = userEvent.setup();
    navigation.search = "tab=preview&path=opt-b3-1";
    renderWithProviders(<ExamReviewView examId={BCA} />);
    const preview = await waitFor(() => screen.getByRole("tabpanel", { name: "Đề xem trước" }));
    await user.click(await within(preview).findByRole("button", { name: "Sinh đề xem trước" }));
    expect(await within(preview).findByText("81 câu · 100 điểm · 180 phút, chung cả bài")).toBeInTheDocument();
    expect(within(preview).getByText(/Đề có câu tự luận chấm tay/)).toBeInTheDocument();
    expect(within(preview).getByRole("heading", { name: /Phần 1 · Tự luận Ngữ văn.*25 \/ 25 điểm/ })).toBeInTheDocument();
    expect(within(preview).getAllByText("Chấm tay").length).toBeGreaterThan(0);
    expect(within(preview).getByText("Tiêu chí chấm")).toBeInTheDocument();
  });
});

describe("kết quả kiểm tra theo bảy kỳ thi", () => {
  it("THPTQG Tin học: số câu làm và in, thời lượng chung, điểm trên thang; cảnh báo không chặn", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId={THPTQG} />);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra cấu hình" }));
    expect(await screen.findByText("28 câu làm (30 câu in trên đề)")).toBeInTheDocument();
    expect(screen.getByText("50 phút, chung cả bài")).toBeInTheDocument();
    expect(screen.getByText("10 / 10 điểm")).toBeInTheDocument();
    expect(backend.app(THPTQG).check.status).toBe("VALID");
    expect(screen.getAllByText(/quy tắc chấm khác quy tắc mong đợi/).length).toBeGreaterThan(0);
  });

  it("BCA: thiếu tiêu chí chấm nêu mã câu nguồn; khối tự luận có nhãn Chấm tay", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId={BCA} />);
    await user.click(await screen.findByRole("button", { name: "Kiểm tra cấu hình" }));
    expect((await screen.findAllByText(/chưa có tiêu chí chấm \(CH-007002\)/)).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Chấm tay").length).toBeGreaterThan(0);
  });
});

describe("hộp thoại công bố", () => {
  it("đường không công bố được nêu vấn đề chặn đầu tiên", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamVersionsView examId="ex-hsa-f-001" />);
    await user.click(await screen.findByRole("button", { name: "Công bố" }));
    const dialog = await screen.findByRole("dialog");
    const row = within(dialog).getByRole("checkbox", { name: /^Khoa học/ }).parentElement!;
    expect(within(row).getByText(/không đủ nguồn cho chỉ tiêu cấp môn/)).toBeInTheDocument();
  });
});
