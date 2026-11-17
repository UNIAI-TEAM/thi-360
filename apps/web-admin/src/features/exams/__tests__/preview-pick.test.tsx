import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamReviewView } from "../components/exam-review-view";
import { ExamsBackend } from "./fake-exams-backend";
import { PREVIEW_HSA_000_SCIENCE_PCG } from "./review-fixtures";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const navigation = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex-hsa-f-000/review",
  useSearchParams: () => new URLSearchParams(navigation.search),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

/** HSA-F-000: mọi cách chọn môn đủ nguồn, nên đường Khoa học sinh được đề. */
const HSA = "ex-hsa-f-000";
const SCIENCE = "opt-sci-4";

beforeEach(() => {
  navigation.search = "";
  window.history.replaceState(null, "", "/");
  // Máy chủ sinh đề: đề của đường Khoa học khi học viên chọn Vật lí, Hóa học, Địa lí (hai môn tự nhiên một môn xã hội).
  new ExamsBackend()
    .seedPreview(HSA, `${SCIENCE}|PHYSICS,CHEMISTRY,GEOGRAPHY`, { status: 200, body: PREVIEW_HSA_000_SCIENCE_PCG })
    .install();
});

async function openSciencePreview() {
  navigation.search = `tab=preview&path=${SCIENCE}`;
  renderWithProviders(<ExamReviewView examId={HSA} />);
  const preview = await waitFor(() => screen.getByRole("tabpanel", { name: "Đề xem trước" }));
  const group = await within(preview).findByRole("group", { name: "Chọn 3 môn để xem trước" });
  // Tên môn lấy từ danh mục: đợi tải xong.
  await within(group).findByRole("checkbox", { name: /^Vật lí/ });
  return { preview, group };
}

describe("xem trước đường Khoa học: chọn 3 môn", () => {
  it("mặc định 3 môn đầu; đủ 3 môn thì khóa các môn còn lại, bỏ một môn thì chưa sinh được", async () => {
    const user = userEvent.setup();
    const { preview, group } = await openSciencePreview();
    const box = (name: RegExp) => within(group).getByRole("checkbox", { name });

    expect(box(/^Vật lí/)).toBeChecked();
    expect(box(/^Hóa học/)).toBeChecked();
    expect(box(/^Sinh học/)).toBeChecked();
    expect(box(/^Lịch sử/)).not.toBeChecked();
    expect(box(/^Lịch sử/)).toBeDisabled();
    expect(box(/^Địa lí/)).toBeDisabled();
    // Ba môn cùng nhóm tự nhiên: một môn nhận 16 câu, chọn theo hạt giống của lần sinh.
    expect(within(preview).getByText(/Cả 3 môn cùng nhóm: một môn nhận 16 câu/)).toBeInTheDocument();

    await user.click(box(/^Sinh học/));
    expect(within(preview).getByText(/Đã chọn 2 \/ 3 môn/)).toBeInTheDocument();
    expect(within(preview).getByRole("button", { name: "Sinh đề xem trước" })).toHaveAttribute("aria-disabled", "true");
    expect(box(/^Lịch sử/)).toBeEnabled();

    // Hai môn tự nhiên một môn xã hội: số câu từng môn chia theo B10, chỉ đọc.
    await user.click(box(/^Lịch sử/));
    expect(within(preview).getByText("Số câu từng môn: Vật lí 17 · Hóa học 17 · Lịch sử 16.")).toBeInTheDocument();
    expect(within(preview).getByRole("button", { name: "Sinh đề xem trước" })).not.toHaveAttribute("aria-disabled", "true");
  });

  it("sinh đề theo 3 môn đã chọn; bảng đối chiếu hiện chỉ tiêu cấp môn của đúng 3 môn kèm số câu B10", async () => {
    const user = userEvent.setup();
    const { preview, group } = await openSciencePreview();
    await user.click(within(group).getByRole("checkbox", { name: /^Sinh học/ }));
    await user.click(within(group).getByRole("checkbox", { name: /^Địa lí/ }));
    expect(within(preview).getByText("Số câu từng môn: Vật lí 17 · Hóa học 17 · Địa lí 16.")).toBeInTheDocument();

    await user.click(within(preview).getByRole("button", { name: "Sinh đề xem trước" }));
    expect(await within(preview).findByText("150 câu · 150 điểm · 195 phút")).toBeInTheDocument();
    await user.click(within(preview).getByRole("tab", { name: "Đối chiếu phân bổ" }));

    const table = await within(preview).findByRole("table", { name: "Đối chiếu chỉ tiêu cấp môn với đề đã sinh" });
    const subjectsOnTable = new Set(
      within(table)
        .getAllByRole("row")
        .slice(1)
        .map((row) => within(row).getAllByRole("cell")[0]!.firstElementChild?.firstElementChild?.textContent),
    );
    expect(subjectsOnTable.has("Vật lí")).toBe(true);
    expect(subjectsOnTable.has("Hóa học")).toBe(true);
    expect(subjectsOnTable.has("Địa lí")).toBe(true);
    expect(subjectsOnTable.has("Sinh học")).toBe(false);
    expect(subjectsOnTable.has("Lịch sử")).toBe(false);
    expect(within(table).getAllByText("16 câu trên đề").length).toBeGreaterThan(0);
    expect(within(table).getAllByText("17 câu trên đề").length).toBeGreaterThan(0);
    expect(within(preview).getByText("Đề sinh khớp mọi chỉ tiêu cấp môn.")).toBeInTheDocument();
  });

  it("đường không có phương án chọn môn không hiện ô chọn môn", async () => {
    navigation.search = "tab=preview&path=opt-30";
    renderWithProviders(<ExamReviewView examId={HSA} />);
    const preview = await waitFor(() => screen.getByRole("tabpanel", { name: "Đề xem trước" }));
    await within(preview).findByRole("button", { name: "Sinh đề xem trước" });
    expect(within(preview).queryByRole("group", { name: /Chọn \d môn để xem trước/ })).not.toBeInTheDocument();
  });
});
