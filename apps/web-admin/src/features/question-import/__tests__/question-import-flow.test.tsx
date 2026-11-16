import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { renderWithProviders } from "@/test/render";
import { importHistoryStore } from "../api";
import { QuestionImportView } from "../components/question-import-view";

function setUrl(search = "") {
  window.history.replaceState(null, "", `/questions/import${search}`);
}

function fileInput(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("no file input");
  return input;
}

async function uploadSample(container: HTMLElement, user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
  await user.upload(fileInput(container), new File(["xlsx"], "ngan-hang-toan-11.xlsx"));
  await user.click(screen.getByRole("button", { name: "Đọc tệp" }));
  return screen.findByRole("heading", { name: "Ánh xạ cột" });
}

describe("Nhập câu hỏi từ tệp", () => {
  beforeEach(() => {
    questionStore.reset();
    importHistoryStore.reset();
    setUrl();
  });

  it("bước 1: vùng chọn tệp, các cột của tệp mẫu, dải dữ liệu mẫu và lịch sử nhập", async () => {
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Nhập câu hỏi từ tệp" })).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Dữ liệu mẫu.");
    expect(screen.getByRole("list", { name: "Các bước nhập câu hỏi" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Các cột của tệp mẫu" })).toBeInTheDocument();
    const history = await screen.findByRole("table", { name: "Lịch sử nhập câu hỏi" });
    expect(within(history).getByText("toan-10-chuong-1.xlsx")).toBeInTheDocument();
    expect(within(history).getByText("120 tạo mới · 3 trùng · 2 lỗi")).toBeInTheDocument();
  });

  it("báo lỗi ngay khi chọn tệp sai loại", async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { container } = renderWithProviders(<QuestionImportView />);
    await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
    await user.upload(fileInput(container), new File(["x"], "de-thi.pdf"));
    expect(screen.getByRole("alert")).toHaveTextContent("Chỉ nhận tệp .xlsx hoặc .csv.");
    expect(screen.getByRole("button", { name: "Đọc tệp" })).toBeDisabled();
  });

  it("?mockState=error: lỗi tải dữ liệu tham chiếu kèm nút thử lại", async () => {
    setUrl("?mockState=error");
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Không tải được dữ liệu tham chiếu" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("?mockState=empty: lịch sử nhập rỗng", async () => {
    setUrl("?mockState=empty");
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Chưa có lần nhập nào." })).toBeInTheDocument();
  });

  it("?mockState=loading: khung chờ", () => {
    setUrl("?mockState=loading");
    renderWithProviders(<QuestionImportView />);
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
  });

  it("đi hết bốn bước: đọc tệp, ánh xạ, kiểm tra, nhập thành Nháp", async () => {
    const user = userEvent.setup();
    const before = questionStore.all().length;
    const { container } = renderWithProviders(<QuestionImportView />);

    const mappingHeading = await uploadSample(container, user);
    await waitFor(() => expect(mappingHeading).toHaveFocus());
    expect(screen.getByText("14 cột đã ánh xạ · 1 chưa ánh xạ · 1 bỏ qua")).toBeInTheDocument();
    expect(screen.getByText(/chưa ánh xạ — câu nhập vào sẽ ở trạng thái Nháp/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Trường của hệ thống cho cột Mã KT" })).toHaveTextContent(
      "— Chọn trường —",
    );

    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    const checkHeading = await screen.findByRole("heading", { name: "Kiểm tra và xem trước" });
    await waitFor(() => expect(checkHeading).toHaveFocus());
    const summary = screen.getByLabelText("Tóm tắt kiểm tra");
    expect(within(summary).getByText("26")).toBeInTheDocument();
    expect(within(summary).getByText("15")).toBeInTheDocument();
    expect(within(summary).getByText("8")).toBeInTheDocument();
    expect(within(summary).getByText("3")).toBeInTheDocument();

    const errors = screen.getByRole("table", { name: "Các dòng lỗi" });
    expect(within(errors).getByText("Thiếu Đáp án đúng")).toBeInTheDocument();
    expect(within(errors).getByText("Môn học “Toan” không có trong danh mục")).toBeInTheDocument();
    expect(window.location.search).toBe("");

    await user.click(screen.getByRole("button", { name: "Xem trước dòng 18" }));
    const preview = await screen.findByRole("heading", { name: "Xem trước dòng 18" });
    await waitFor(() => expect(preview).toHaveFocus());
    const previewSection = preview.closest("section");
    if (!previewSection) throw new Error("no preview section");
    expect(within(previewSection).getByText(/Môn học “Toan” không có trong danh mục/)).toBeInTheDocument();
    expect(within(previewSection).getByText("Có lỗi")).toBeInTheDocument();

    // UNI-2599: câu Tự luận chưa có tiêu chí chấm vẫn hợp lệ, kèm cảnh báo (câu ở Nháp tới khi bổ sung).
    await user.click(screen.getByRole("tab", { name: "Hợp lệ (15)" }));
    await user.click(screen.getByRole("button", { name: "Xem trước dòng 19" }));
    const essayPreview = await screen.findByRole("heading", { name: "Xem trước dòng 19" });
    const essaySection = essayPreview.closest("section");
    if (!essaySection) throw new Error("no essay preview section");
    expect(within(essaySection).getByText(/Câu tự luận chưa có tiêu chí chấm/)).toBeInTheDocument();
    expect(within(essaySection).getByText("Chưa có tiêu chí chấm; câu sẽ ở Nháp tới khi bổ sung.")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Trùng (3)" }));
    expect(window.location.search).toBe("?tab=duplicates");
    expect(screen.getByRole("link", { name: "CH-000004" })).toHaveAttribute("href", "/questions/q-0004");

    await user.click(screen.getByRole("button", { name: "Nhập 15 câu" }));
    const resultHeading = await screen.findByRole("heading", { name: "Đã nhập xong 13 câu" });
    await waitFor(() => expect(resultHeading).toHaveFocus());
    expect(screen.getByRole("link", { name: "Xem các câu vừa nhập" })).toHaveAttribute(
      "href",
      "/questions?status=DRAFT",
    );
    expect(screen.getByRole("button", { name: "Tải file lỗi" })).toBeInTheDocument();
    expect(questionStore.all().length).toBe(before + 13);
    expect(questionStore.all()[0]?.status).toBe("DRAFT");
    expect(window.location.search).toBe("");

    await user.click(screen.getByRole("button", { name: "Nhập tệp khác" }));
    expect(await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" })).toHaveFocus();
  }, 20_000);

  it("không bỏ qua dòng lỗi thì không nhập và báo trong màn", async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);
    await uploadSample(container, user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    await screen.findByRole("heading", { name: "Kiểm tra và xem trước" });

    const skip = screen.getByRole("checkbox", { name: "Bỏ qua 8 dòng lỗi, chỉ nhập các câu hợp lệ" });
    await user.click(skip);
    await user.click(screen.getByRole("button", { name: "Nhập 15 câu" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Tệp còn dòng lỗi.");
    expect(skip).toHaveFocus();
  });

  it("thiếu cột bắt buộc thì không qua được bước ánh xạ", async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);
    await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
    const csv = "Mã nguồn,Dạng câu,Nội dung,A,B,Đáp án,Môn,Lớp\nX-1,Trắc nghiệm 1 đáp án,Câu 1,1,2,A,Toán,11\n";
    await user.upload(fileInput(container), new File([csv], "thieu-cot.csv", { type: "text/csv" }));
    await user.click(screen.getByRole("button", { name: "Đọc tệp" }));
    await screen.findByRole("heading", { name: "Ánh xạ cột" });

    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Chưa ánh xạ trường bắt buộc: Mức độ.");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.queryByRole("heading", { name: "Kiểm tra và xem trước" })).not.toBeInTheDocument();
  });
});
