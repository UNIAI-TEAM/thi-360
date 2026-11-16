import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { renderWithProviders } from "@/test/render";
import { getExam } from "../api";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamReviewView } from "../components/exam-review-view";
import { reviewStore } from "../mock/review-store";
import { examStore } from "../mock/store";
import { useInMemoryBackend } from "./in-memory-backend";

useInMemoryBackend();

const navigation = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex-hsa-f-001/review",
  useSearchParams: () => new URLSearchParams(navigation.search),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const HSA = "ex-hsa-f-001";

beforeEach(() => {
  examStore.reset();
  questionStore.reset();
  reviewStore.reset();
  navigation.search = "";
  window.history.replaceState(null, "", "/");
});

afterEach(() => {
  window.history.replaceState(null, "", "/");
});

function panel(name: string) {
  return screen.getByRole("tabpanel", { name });
}

describe("ExamReviewView", () => {
  it("hien ket qua kiem tra theo tung duong, Khoa hoc thieu nguon Hoa hoc Lop 12 o chi tieu cap mon", async () => {
    renderWithProviders(<ExamReviewView examId={HSA} />);
    const check = await screen.findByRole("heading", { name: "Kết quả theo đường lựa chọn" });
    expect(check).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Kết quả kiểm tra của 2 đường lựa chọn" });
    expect(within(table).getAllByRole("row")).toHaveLength(3);
    expect(within(table).getAllByText("Hợp lệ")).toHaveLength(1);
    expect(within(table).getByText("Không hợp lệ")).toBeInTheDocument();
    // Mỗi đường nêu vấn đề chặn đầu tiên: phương án Khoa học thiếu nguồn Hóa học Lớp 12 ở chỉ tiêu cấp môn.
    expect(within(table).getByText(/không đủ nguồn cho chỉ tiêu cấp môn/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đến dòng ma trận cần sửa của Khoa học" })).toBeInTheDocument();
  });

  it("bai chua kiem tra hien trang thai trong, chay kiem tra thi co ket qua", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId="ex-hsa-f-002" />);
    expect(await screen.findByRole("heading", { name: "Chưa kiểm tra cấu hình" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu hình" }));
    expect(await screen.findByRole("heading", { name: "Kết quả theo đường lựa chọn" })).toBeInTheDocument();
    expect(examStore.require("ex-hsa-f-002").check.status).not.toBe("UNCHECKED");
    await waitFor(() =>
      expect(screen.getAllByRole("status").some((node) => /Đã kiểm tra:/.test(node.textContent ?? ""))).toBe(true),
    );
  });

  it("duong Khoa hoc hien ket qua tung bo mon hoc vien chon kem so cau tung mon, bo ba mon tu nhien co ba cach chia", async () => {
    renderWithProviders(<ExamReviewView examId={HSA} />);
    const table = await screen.findByRole("table", { name: /^Kết quả kiểm tra từng bộ môn của đường/ });
    // Tên môn lấy từ danh mục: đợi tải xong.
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(13));
    const rowOf = (text: string) => within(table).getByText(text).closest("tr")!;
    expect(screen.getByText(/12 trường hợp: 1 hợp lệ, 8 không hợp lệ, 3 chưa xác nhận được\./)).toBeInTheDocument();
    expect(screen.getByText(/Hai môn cùng nhóm \(tự nhiên hoặc xã hội\) nhận 17 câu mỗi môn, môn còn lại nhận 16 câu/)).toBeInTheDocument();
    // Ba môn tự nhiên: mỗi môn một lần nhận 16 câu, đều được kiểm tra.
    for (const text of [
      "Vật lí 16 · Hóa học 17 · Sinh học 17",
      "Vật lí 17 · Hóa học 16 · Sinh học 17",
      "Vật lí 17 · Hóa học 17 · Sinh học 16",
    ]) {
      expect(within(rowOf(text)).getByText("Không hợp lệ")).toBeInTheDocument();
    }
    expect(within(rowOf("Vật lí 17 · Sinh học 17 · Lịch sử 16")).getByText("Hợp lệ")).toBeInTheDocument();
    expect(within(rowOf("Vật lí 17 · Sinh học 17 · Địa lí 16")).getByText("Chưa xác nhận được")).toBeInTheDocument();
    // Hai môn xã hội một môn tự nhiên: môn tự nhiên nhận 16 câu.
    expect(within(rowOf("Vật lí 16 · Lịch sử 17 · Địa lí 17")).getByText("Chưa xác nhận được")).toBeInTheDocument();
  });

  it("sinh de xem truoc tu bang kiem tra cho duong Tieng Anh", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamReviewView examId={HSA} />);
    // Đợi danh mục (tên khối lớp, độ khó) tải xong: bảng dựng lại ô một lần khi tên đổi.
    await waitFor(() => expect(screen.getAllByText(/cần 9 câu/)[0]).not.toHaveTextContent("G12"));
    await user.click(screen.getAllByRole("button", { name: /^Sinh đề xem trước cho Tiếng Anh/ })[0]!);
    expect(screen.getByRole("tab", { name: "Đề xem trước" })).toHaveAttribute("aria-selected", "true");
    const preview = panel("Đề xem trước");
    expect(await within(preview).findByText("150 câu · 150 điểm · 195 phút")).toBeInTheDocument();
    expect(window.location.search).toContain("tab=preview");
    expect(window.location.search).toContain("path=opt-30");
  });

  it("duong khong hop le khong sinh de thieu cau", async () => {
    const user = userEvent.setup();
    navigation.search = "tab=preview&path=opt-sci-1";
    renderWithProviders(<ExamReviewView examId={HSA} />);
    const preview = await waitFor(() => panel("Đề xem trước"));
    await user.click(await within(preview).findByRole("button", { name: "Sinh đề xem trước" }));
    expect(await within(preview).findByRole("heading", { name: /^Không sinh được đề cho Khoa học/ })).toBeInTheDocument();
    expect(within(preview).getByText("Không trả về đề thiếu câu, không nới điều kiện ma trận.")).toBeInTheDocument();
  });
});

describe("ExamReviewCheck", () => {
  it("hien trang thai loi khi khong tai duoc ket qua kiem tra", async () => {
    const exam = await getExam(HSA);
    window.history.replaceState(null, "", "/?mockState=error");
    renderWithProviders(<ExamReviewCheck exam={exam} onPreview={() => {}} />);
    expect(await screen.findByText("Không tải được kết quả kiểm tra")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });
});
