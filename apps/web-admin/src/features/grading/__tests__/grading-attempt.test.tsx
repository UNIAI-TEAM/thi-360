import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { GradingAttemptView } from "../components/grading-attempt-view";
import { attemptStore } from "../mock/store";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => window.location.pathname,
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

function setUrl(path: string) {
  window.history.replaceState(null, "", path);
}

beforeEach(() => setUrl("/grading/att-spt-001"));

afterEach(() => {
  attemptStore.reset();
  setUrl("/grading");
});

async function fill(label: string, value: string) {
  const input = screen.getByLabelText(label);
  await userEvent.clear(input);
  await userEvent.type(input, value);
}

describe("GradingAttemptView", () => {
  it("hien de, bai lam va tieu chi; diem vuot toi da bao loi tai o", async () => {
    renderWithProviders(<GradingAttemptView attemptId="att-spt-001" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Bài làm của Bùi Khánh Linh" })).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Dữ liệu mẫu.");
    const navigator = screen.getByRole("navigation", { name: "Các câu tự luận của lượt thi" });
    expect(within(navigator).getAllByRole("button")).toHaveLength(3);
    expect(within(navigator).getByRole("button", { current: true })).toHaveTextContent("Tự luận · Câu 23");
    expect(screen.getByRole("heading", { level: 2, name: "Tự luận · Câu 23" })).toBeInTheDocument();
    expect(screen.getByText("Giải phương trình log₂(x − 1) + log₂(x + 1) = 3.")).toBeInTheDocument();
    expect(screen.getByText((_content, element) => element?.textContent?.startsWith("Điều kiện: x > 1.") ?? false)).toBeInTheDocument();

    const first = screen.getByLabelText("1. Đặt đúng điều kiện xác định x > 1.");
    await userEvent.type(first, "0,5");
    await userEvent.tab();
    expect(await screen.findByText("Điểm không được vượt quá 0,25.")).toBeInTheDocument();
    expect(first).toHaveAttribute("aria-invalid", "true");

    await userEvent.clear(first);
    await userEvent.type(first, "0.25");
    await waitFor(() => expect(screen.queryByText("Điểm không được vượt quá 0,25.")).not.toBeInTheDocument());
  });

  it("luu nhap ghi diem va nhan xet vao kho, luot van Dang cham", async () => {
    renderWithProviders(<GradingAttemptView attemptId="att-spt-001" />);
    await screen.findByRole("heading", { level: 1, name: "Bài làm của Bùi Khánh Linh" });

    await fill("2. Biến đổi về phương trình x² − 1 = 8.", "0,5");
    await userEvent.type(screen.getByLabelText("Nhận xét"), "Trình bày rõ");
    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));

    await waitFor(() => expect(attemptStore.require("att-spt-001").essays[0]?.scores[1]?.score).toBe(0.5));
    const record = attemptStore.require("att-spt-001");
    expect(record.status).toBe("GRADING");
    expect(record.essays[0]?.comment).toBe("Trình bày rõ");
    expect(record.grader?.id).toBe("u-grader-01");
  });

  it("hoan tat khi con o trong: bao loi, chuyen toi o dau tien; du diem thi xac nhan trong trang va co tong diem", async () => {
    renderWithProviders(<GradingAttemptView attemptId="att-spt-001" />);
    await screen.findByRole("heading", { level: 1, name: "Bài làm của Bùi Khánh Linh" });

    await fill("1. Đặt đúng điều kiện xác định x > 1.", "0,25");
    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const missing = screen.getByLabelText("2. Biến đổi về phương trình x² − 1 = 8.");
    await waitFor(() => expect(missing).toHaveFocus());
    expect(missing).toHaveAttribute("aria-invalid", "true");
    expect(screen.queryByRole("region", { name: /Hoàn tất chấm bài/ })).not.toBeInTheDocument();

    await fill("2. Biến đổi về phương trình x² − 1 = 8.", "0,5");
    await fill("3. Kết luận nghiệm x = 3, loại x = −3.", "0,25");
    await userEvent.click(screen.getByRole("button", { name: "Câu sau" }));
    await waitFor(() => expect(screen.getByRole("heading", { level: 2, name: "Tự luận · Câu 24" })).toHaveFocus());
    await fill("1. Chỉ ra AC là hình chiếu của SC trên (ABCD), góc cần tìm là góc SCA.", "0,5");
    await fill("2. Tính AC = a√2, tan SCA = 1, kết luận góc bằng 45°.", "0,5");
    await userEvent.click(screen.getByRole("button", { name: "Câu sau" }));
    await fill("1. Lập đúng hàm lợi nhuận theo số lần giảm giá x: (40 − 2x)(300 + 20x).", "0,5");
    await fill("2. Tìm được giá trị lớn nhất tại x = 2,5.", "0,25");
    await fill("3. Kết luận giá bán 95 nghìn đồng một chiếc.", "0");

    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirm = await screen.findByRole("region", { name: "Hoàn tất chấm bài của Bùi Khánh Linh?" });
    await waitFor(() => expect(confirm).toHaveFocus());
    expect(confirm).toHaveTextContent("trắc nghiệm 5,25 + tự luận 2,75 = 8/10");

    await userEvent.click(within(confirm).getByRole("button", { name: "Xác nhận hoàn tất" }));
    const result = await screen.findByRole("heading", { level: 2, name: "Đã có điểm" });
    await waitFor(() => expect(result).toHaveFocus());
    expect(screen.queryByRole("button", { name: "Hoàn tất chấm" })).not.toBeInTheDocument();
    expect(screen.getByText("8/10")).toBeInTheDocument();
    expect(attemptStore.require("att-spt-001").status).toBe("SCORED");
  });

  it("luot da co diem chi de doc, hien tong diem", async () => {
    setUrl("/grading/att-bca-003");
    renderWithProviders(<GradingAttemptView attemptId="att-bca-003" />);
    expect(await screen.findByRole("heading", { level: 2, name: "Đã có điểm" })).toBeInTheDocument();
    expect(screen.getByText("58,25/100")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu nháp" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByText("Bài viết quá ngắn, thiếu dẫn chứng; cần triển khai luận điểm rõ hơn.")).toBeInTheDocument();
  });

  it("mo dung cau theo ?question= va hien doan doc hieu cua cau chum", async () => {
    setUrl("/grading/att-thpt-001?question=gq-thpt-van-dh3");
    renderWithProviders(<GradingAttemptView attemptId="att-thpt-001" />);
    expect(await screen.findByRole("heading", { level: 2, name: "Đọc hiểu · Câu 3" })).toBeInTheDocument();
    expect(screen.getByText("Đoạn đọc hiểu (CH-TL-THPT-010)")).toBeInTheDocument();
    expect(screen.getByText("Đề không có phần trắc nghiệm")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Viết · Câu 2/ }));
    expect(screen.getByRole("heading", { level: 2, name: "Viết · Câu 2" })).toBeInTheDocument();
    expect(window.location.search).toBe("?question=gq-thpt-van-v2");
  });

  it("khong tim thay luot thi", async () => {
    renderWithProviders(<GradingAttemptView attemptId="khong-co" />);
    expect(await screen.findByText("Không tìm thấy lượt thi")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại danh sách chấm" })).toHaveAttribute("href", "/grading");
  });

  it("trang thai loi va rong theo ?mockState", async () => {
    setUrl("/grading/att-spt-001?mockState=error");
    const { unmount } = renderWithProviders(<GradingAttemptView attemptId="att-spt-001" />);
    expect(await screen.findByText("Không tải được lượt thi")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
    unmount();

    setUrl("/grading/att-spt-001?mockState=empty");
    renderWithProviders(<GradingAttemptView attemptId="att-spt-001" />);
    expect(await screen.findByText("Lượt thi không có câu tự luận")).toBeInTheDocument();
  });
});
