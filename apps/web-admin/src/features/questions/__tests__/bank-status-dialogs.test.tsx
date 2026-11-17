import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { toQuestionSummary } from "../api";
import { MarkReadyDialog, RetireQuestionDialog } from "../components/question-status-dialogs";
import { apiReadiness, withQuestionsServer } from "./api-fixtures";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/questions",
  useSearchParams: () => new URLSearchParams(),
}));

// q-0006 là câu Nháp chưa chọn đáp án đúng: máy chủ báo thiếu điều kiện ANSWER.
const fakeQuestions = withQuestionsServer({
  readiness: { "q-0006": apiReadiness(false, [{ code: "NO_CORRECT_OPTION", path: "body.options" }]) },
  usage: {
    "q-0003": { examIds: ["ex-1", "ex-2", "ex-3"], ledger: [{ examId: "ex-1", kind: "ATTEMPT", versionNo: 1 }] },
  },
});

const summary = (id: string) => toQuestionSummary(fakeQuestions.require(id));

describe("MarkReadyDialog (canvas KiemTraSanSang)", () => {
  it("cau chua du dieu kien: liet ke dieu kien chua dat va lien ket den cho can sua", async () => {
    const onOpenChange = vi.fn();
    renderWithProviders(<MarkReadyDialog question={summary("q-0006")} open onOpenChange={onOpenChange} />);

    const dialog = await screen.findByRole("dialog", { name: "Chưa chuyển được sang Sẵn sàng" });
    expect(dialog).toHaveTextContent("Còn 1 trên 7 điều kiện chưa đạt.");
    const items = within(dialog).getAllByRole("listitem").filter((item) => /^\d\./.test(item.textContent ?? ""));
    expect(items).toHaveLength(7);
    const failed = items.find((item) => item.textContent?.includes("đáp án hợp lệ"))!;
    expect(failed).toHaveTextContent("Chưa đạt");
    expect(failed).toHaveTextContent("Chưa chọn đáp án đúng.");
    expect(within(failed).getByRole("link", { name: "Đến chỗ cần sửa: Chưa chọn đáp án đúng." })).toHaveAttribute(
      "href",
      "/questions/q-0006/edit#question-answers",
    );
    expect(within(dialog).queryByRole("button", { name: "Chuyển sang Sẵn sàng" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Sửa câu hỏi" })).toHaveAttribute("href", "/questions/q-0006/edit");
    expect(fakeQuestions.require("q-0006").status).toBe("DRAFT");
  });

  it("cau du dieu kien: xac nhan thi chuyen trang thai va bao cho ben goi", async () => {
    const onDone = vi.fn();
    const onOpenChange = vi.fn();
    renderWithProviders(
      <MarkReadyDialog question={summary("q-0002")} open onOpenChange={onOpenChange} onDone={onDone} />,
    );
    const dialog = await screen.findByRole("dialog", { name: "Chuyển câu hỏi sang Sẵn sàng" });
    await userEvent.click(await within(dialog).findByRole("button", { name: "Chuyển sang Sẵn sàng" }));
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ id: "q-0002", status: "READY" })));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("hien tieng Anh", async () => {
    renderWithProviders(<MarkReadyDialog question={summary("q-0006")} open onOpenChange={() => {}} />, {
      locale: "en",
      messages: en,
    });
    expect(await screen.findByRole("dialog", { name: "Cannot mark as ready yet" })).toBeInTheDocument();
  });
});

describe("RetireQuestionDialog (canvas NgungCauHoi)", () => {
  it("neu he qua, bai thi dang dung va khong xoa han; xac nhan thi ngung su dung", async () => {
    const onDone = vi.fn();
    const question = summary("q-0003");
    renderWithProviders(<RetireQuestionDialog question={question} open onOpenChange={() => {}} onDone={onDone} />);

    const dialog = await screen.findByRole("dialog", { name: "Ngừng sử dụng câu hỏi CH-000003?" });
    expect(dialog).toHaveTextContent("Lượt thi đang làm và kết quả cũ vẫn hiện đúng câu hỏi này");
    const table = await within(dialog).findByRole("table", { name: /Bài thi đang dùng câu hỏi CH-000003/ });
    expect(within(table).getAllByRole("row")).toHaveLength(question.usedInExamCount + 1);
    expect(dialog).toHaveTextContent("Không xóa được: câu hỏi đã có trong lịch sử thi.");
    expect(within(dialog).getByRole("button", { name: "Giữ lại" })).toHaveFocus();

    await userEvent.click(within(dialog).getByRole("button", { name: "Ngừng sử dụng" }));
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ status: "RETIRED" })));
    expect(fakeQuestions.require("q-0003").status).toBe("RETIRED");
  });

  it("cau chua dung o bai thi nao", async () => {
    renderWithProviders(<RetireQuestionDialog question={summary("q-0002")} open onOpenChange={() => {}} />);
    expect(await screen.findByText("Câu hỏi chưa được dùng trong bài thi nào.")).toBeInTheDocument();
    expect(screen.queryByText("Không xóa được: câu hỏi đã có trong lịch sử thi.")).not.toBeInTheDocument();
  });
});
