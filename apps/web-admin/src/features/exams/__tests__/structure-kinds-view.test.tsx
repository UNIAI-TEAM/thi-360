import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { examStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
});

function openStructure(examId: string, search = "") {
  window.history.replaceState(null, "", `/exams/${examId}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

describe("Cấu trúc theo bảy kỳ thi", () => {
  it("THPTQG Tin học: thời lượng chung không có ô nhập; phần chung và hai nhánh; thêm nhánh", async () => {
    const user = userEvent.setup();
    openStructure("ex-thptqg-tin-001", "?part=part-t2");
    expect(await screen.findByRole("heading", { name: "Phần II · Đúng/Sai" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /Thời lượng \(phút\)/ })).not.toBeInTheDocument();
    expect(screen.getByText("50 phút, dùng chung cả bài")).toBeInTheDocument();
    expect(screen.getByText("28 câu làm (30 câu in trên đề)")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Phần chung của mọi nhánh" })).toBeInTheDocument();
    expect(screen.getByText("Nhánh (2)")).toBeInTheDocument();
    expect(screen.getAllByText(/Khoa học máy tính/).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Thêm nhánh" }));
    const dialog = await screen.findByRole("dialog", { name: "Thêm nhánh" });
    await user.click(within(dialog).getByRole("button", { name: "Thêm nhánh" }));
    expect(await within(dialog).findByText("Nhập tên nhánh.")).toBeInTheDocument();
    await user.type(within(dialog).getByRole("textbox", { name: /Tên nhánh/ }), "Lập trình");
    await user.click(within(dialog).getByRole("button", { name: "Thêm nhánh" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByText("Nhánh (3)")).toBeInTheDocument();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });

  it("BCA: điểm phần và thang 100; tổ hợp chờ chuyên gia xác nhận được xác nhận tại chỗ", async () => {
    const user = userEvent.setup();
    openStructure("ex-bca-f-001", "?part=part-b3");
    expect(await screen.findByRole("heading", { name: "Phần 3 · Tự chọn" })).toBeInTheDocument();
    expect(screen.getByText("100 / 100 điểm")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("15");
    expect(screen.getAllByText("Chờ chuyên gia xác nhận").length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: /^Xác nhận số câu của CA4/ }));
    expect(screen.queryByRole("button", { name: /^Xác nhận số câu của CA4/ })).not.toBeInTheDocument();
    expect(screen.getAllByText("Chuyên gia đã xác nhận")).toHaveLength(4);
  });

  it("QDA: không còn nút sinh tổ hợp; gắn tổ hợp danh mục QD02 vào phần tự chọn", async () => {
    // HSA đã chuyển sang một phương án chọn môn; QDA, BCA giữ màn tổ hợp (gắn từ danh mục, không sinh).
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=" + examStore.require("ex-qda-f-001").parts[2]!.id);
    expect(await screen.findByRole("heading", { name: "Phần 3 · Khoa học hoặc Tiếng Anh" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sinh tổ hợp" })).not.toBeInTheDocument();
    expect(screen.queryByText("QD02")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Gắn tổ hợp" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("radio", { name: /QD02/ }));
    await user.click(within(dialog).getByRole("button", { name: "Gắn tổ hợp" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getAllByText(/QD02/).length).toBeGreaterThan(0);
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });

  it("V-ACT: khối Tư duy khoa học chia theo năng lực", async () => {
    openStructure("ex-vact-f-001", "?block=blk-v3");
    expect(await screen.findByRole("switch", { name: "Phân bổ theo năng lực" })).toBeChecked();
    expect(screen.getByRole("table", { name: "Theo năng lực" })).toBeInTheDocument();
    expect(screen.getAllByText(/Logic, phân tích số liệu/).length).toBeGreaterThan(0);
  });
});
