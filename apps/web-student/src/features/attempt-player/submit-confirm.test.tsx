import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { SubmitConfirmDialog } from "./submit-confirm-dialog";

describe("SubmitConfirmDialog (AC-35, AC-19)", () => {
  it("hiển thị tổng kết từng phần và cảnh báo từ tự luận dưới mức gợi ý nhưng KHÔNG chặn nút Nộp bài", async () => {
    const user = userEvent.setup();
    const onConfirmSubmit = vi.fn(async () => undefined);

    renderWithProviders(
      <SubmitConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        answeredCount={38}
        partialCount={1}
        blankCount={1}
        flaggedCount={2}
        totalCount={40}
        partBreakdown={[
          { ord: 1, partName: "Phần 1 · Trắc nghiệm", answeredCount: 38, totalCount: 39, missingCount: 1 },
          { ord: 2, partName: "Phần 2 · Tự luận", answeredCount: 0, totalCount: 1, missingCount: 1 },
        ]}
        essayWarnings={[
          { itemId: "essay-1", displayNo: 40, wordCount: 120, wordLimit: 300 },
        ]}
        essayStatus={{ wordCount: 120, unsavedWords: true }}
        status="idle"
        onConfirmSubmit={onConfirmSubmit}
        onReconcile={vi.fn(async () => undefined)}
      />,
    );

    expect(screen.getByText("38/40 câu đã làm")).toBeInTheDocument();
    expect(screen.getByText("Phần 1 · Trắc nghiệm")).toBeInTheDocument();
    expect(screen.getByText("Còn thiếu 1/39 câu")).toBeInTheDocument();
    expect(screen.getByText("Phần 2 · Tự luận")).toBeInTheDocument();
    expect(screen.getByText("Câu 40: 120/300 chữ — chưa đủ số chữ tối thiểu")).toBeInTheDocument();
    expect(screen.getByText("120 chữ · chưa lưu")).toBeInTheDocument();

    // Non-blocking: submit button MUST stay enabled!
    const submitBtn = screen.getByRole("button", { name: "Nộp bài" });
    expect(submitBtn).toBeEnabled();
    await user.click(submitBtn);
    expect(onConfirmSubmit).toHaveBeenCalledTimes(1);
  });

  it("chuyển sang nút Kiểm tra lại trạng thái khi nộp bài chưa xác nhận (AC-19)", async () => {
    const user = userEvent.setup();
    const onReconcile = vi.fn(async () => undefined);

    renderWithProviders(
      <SubmitConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        answeredCount={10}
        partialCount={0}
        blankCount={0}
        flaggedCount={0}
        totalCount={10}
        partBreakdown={[]}
        essayWarnings={[]}
        status="unconfirmed"
        onConfirmSubmit={vi.fn(async () => undefined)}
        onReconcile={onReconcile}
      />,
    );

    expect(screen.getByText("Chưa xác nhận nộp bài")).toBeInTheDocument();
    const reconcileBtn = screen.getByRole("button", { name: "Đang kiểm tra trạng thái lượt…" });
    await user.click(reconcileBtn);
    expect(onReconcile).toHaveBeenCalledTimes(1);
  });
});
