import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { examStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
});

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
});

async function renderPage() {
  const user = userEvent.setup();
  renderWithProviders(<CreateExamPage />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-F-003"));
  return user;
}

function summary() {
  return screen.getByRole("complementary", { name: "Tóm tắt" });
}

describe("trang Tạo bài thi", () => {
  it("nạp mẫu HSA FULL: 3 phần, 150 câu, 195 phút, 4 đường lựa chọn; tổ hợp 17–17–16", async () => {
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Tạo bài thi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("href", "/exams");
    const aside = summary();
    expect(within(aside).getByText("150 câu")).toBeInTheDocument();
    expect(within(aside).getByText("195 phút")).toBeInTheDocument();
    expect(within(aside).getByText("Tiếng Anh + 3 tổ hợp")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Phần tự chọn · Phần 3" })).toBeInTheDocument();
    expect(screen.getAllByText("50 / 50 câu")).toHaveLength(3);
    expect(screen.getByRole("checkbox", { name: "Cho chọn Tiếng Anh (50 câu)" })).toBeChecked();
  });

  it("tổ hợp 51/50 báo thừa 1 câu, chặn tạo; sửa đúng thì tạo và mở Cấu trúc", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA FULL · Đề thử");
    const lastBiology = screen.getAllByRole("textbox", { name: "3. Sinh học" }).at(-1)!;
    await user.clear(lastBiology);
    await user.type(lastBiology, "17");
    expect(await screen.findByText("51 / 50 câu · thừa 1 câu")).toBeInTheDocument();
    const aside = summary();
    expect(within(aside).getByText("Tổng số câu các môn là 51, phải bằng 50 câu của phần.")).toBeInTheDocument();

    await user.click(within(aside).getByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" }));
    expect(routerMock.push).not.toHaveBeenCalled();

    await user.clear(lastBiology);
    await user.type(lastBiology, "16");
    await waitFor(() => expect(within(aside).getByText("Không còn mục cần xử lý")).toBeInTheDocument());
    await user.click(within(aside).getByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const created = examStore.all().find((exam) => exam.code === "HSA-F-003")!;
    expect(created).toMatchObject({ name: "HSA FULL · Đề thử", status: "DRAFT", totalQuestions: 150, combinationCount: 3 });
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${created.id}/structure`);
  }, 20_000);

  it("đổi kỳ thi sau khi đã sửa cấu trúc thì hỏi tại chỗ; nạp lại mẫu QDA", async () => {
    const user = await renderPage();
    const firstCount = screen.getAllByRole("textbox", { name: /^Số câu/ })[0]!;
    await user.clear(firstCount);
    await user.type(firstCount, "40");
    await user.click(screen.getByRole("radio", { name: /QDA/ }));
    const prompt = await screen.findByRole("alert", { name: "" });
    expect(within(prompt).getByText("Nạp lại mẫu QDA FULL?")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /HSA/ })).toBeChecked();

    await user.click(within(prompt).getByRole("button", { name: "Nạp lại mẫu" }));
    expect(screen.getByRole("radio", { name: /QDA/ })).toBeChecked();
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("QDA-F-003"));
    expect(screen.getAllByText("50 / 50 câu")).toHaveLength(4);
    expect(screen.getAllByRole("textbox", { name: "1. Vật lí" })[0]).toHaveValue("25");
    expect(within(summary()).getByText("195 phút")).toBeInTheDocument();
  }, 20_000);

  it("bài lẻ: chọn phần/môn, mã có đoạn môn", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /Bài lẻ/ }));
    expect(screen.getByRole("radio", { name: "Toán" })).toBeChecked();
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-L-TOAN-002"));
    await user.click(screen.getByRole("radio", { name: "Khoa học" }));
    expect(screen.getByRole("heading", { name: "Phần tự chọn · Phần 1" })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /Cho chọn Tiếng Anh/ })).not.toBeInTheDocument();
  });

  it("mã đã dùng: báo lỗi ở ô Mã", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "Trùng mã");
    const code = screen.getByRole("textbox", { name: /Mã bài thi/ });
    await user.clear(code);
    await user.type(code, "HSA-F-001");
    await user.click(within(summary()).getByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" }));
    await waitFor(() => expect(code).toHaveAttribute("aria-invalid", "true"));
    expect(await screen.findAllByText("Mã bài thi đã được dùng. Nhập mã khác.")).not.toHaveLength(0);
    expect(within(summary()).getByText("Mã bài thi")).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("ảnh giới thiệu dùng nút của bộ giao diện, không lộ chữ tiếng Anh của trình duyệt", async () => {
    await renderPage();
    expect(screen.getByText("Chọn ảnh")).toBeInTheDocument();
    expect(screen.getByText("Chưa chọn ảnh")).toBeInTheDocument();
    expect(screen.getByLabelText("Chọn ảnh")).toHaveAttribute("type", "file");
  });

  it("BCA: thời lượng chung 180 phút, chia điểm theo phần 25/60/15, tổ hợp CA1–CA4", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /BCA/ }));
    expect(await screen.findByRole("textbox", { name: /Thời lượng chung/ })).toHaveValue("180");
    expect(screen.getByRole("switch", { name: /Chia điểm theo phần/ })).toBeChecked();
    expect(screen.getByText("Tổng điểm các phần: 100 / 100 điểm")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("BCA-F-002"));
    expect(screen.queryByRole("radio", { name: /Bài lẻ/ })).not.toBeInTheDocument();
    const aside = summary();
    expect(within(aside).getByText("4 tổ hợp")).toBeInTheDocument();
    expect(within(aside).getByText("100 điểm")).toBeInTheDocument();
  }, 20_000);

  it("SP2E: chọn môn, mẫu theo năm; cấu trúc 2026 dùng ma trận 2025 thì nhắc chuyên gia xác nhận", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /SP2E/ }));
    expect(await screen.findByRole("combobox", { name: /Môn của bài thi/ })).toHaveTextContent("Toán");
    expect(screen.getByRole("radio", { name: /^Cấu trúc 2026/ })).toBeChecked();
    expect(screen.getAllByText(/Cấu trúc 2026 dùng ma trận 2025/).length).toBeGreaterThan(0);
    expect(screen.getByRole("checkbox", { name: /Chuyên gia đã xác nhận/ })).not.toBeChecked();
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("SP2E-F-TOAN-001"));

    await user.click(screen.getByRole("radio", { name: /^Cấu trúc 2025/ }));
    await waitFor(() => expect(screen.queryAllByText(/Cấu trúc 2026 dùng ma trận 2025/)).toHaveLength(0));
  }, 20_000);

  it("THPTQG: môn chưa có mẫu thì dựng một phần trống của môn", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /THPTQG/ }));
    await user.click(await screen.findByRole("combobox", { name: /Môn của bài thi/ }));
    await user.click(await screen.findByRole("option", { name: "Tiếng Việt" }));
    expect(await screen.findByText(/Tiếng Việt chưa có mẫu cấu trúc/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("THPTQG-F-TV-001"));
  }, 20_000);
});
