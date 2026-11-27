import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { QuestionTypePicker } from "../components/question-type-picker";
import { fakeQuestions, renderEn } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

describe("chọn dạng câu", () => {
  it("chọn một dạng rồi Tiếp tục mở biểu mẫu của dạng đó", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionTypePicker />);
    const group = screen.getByRole("group", { name: "Question type" });
    expect(within(group).getAllByRole("radio")).toHaveLength(8);
    await user.click(within(group).getAllByRole("radio")[1]!);
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(navigation.push).toHaveBeenCalledWith("/questions/new?type=MULTIPLE_CHOICE");
  });
});

describe("biểu mẫu soạn câu hỏi", () => {
  it("lưu câu một đáp án chưa chọn đáp án đúng thì báo lỗi và không gửi", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    await user.click(await screen.findByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Choose exactly one correct option.")).toBeInTheDocument();
    expect(screen.getByText("Select a subject.")).toBeInTheDocument();
    expect(screen.getByText("Enter the question text.")).toBeInTheDocument();
    expect(screen.getByText("Not saved: some fields are invalid.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("thêm phương án và chọn đáp án đúng", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    const group = await screen.findByRole("group", { name: "Options, choose one correct answer" });
    expect(within(group).getAllByRole("radio")).toHaveLength(4);
    await user.click(screen.getByRole("button", { name: "Add option" }));
    expect(within(group).getAllByRole("radio")).toHaveLength(5);
    await user.click(within(group).getByRole("radio", { name: "Option B is correct" }));
    expect(within(group).getByRole("radio", { name: "Option B is correct" })).toBeChecked();
  });

  it("sửa câu Sẵn sàng báo trước sẽ tạo phiên bản mới", async () => {
    renderEn(<QuestionEditorView mode="edit" questionId="q-0003" />);
    expect(await screen.findByText("Saving creates a new version v3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save question" })).toBeInTheDocument();
  });

  it("UNI-2825: trang sửa có lối quay lại Chi tiết câu hỏi ở trên tiêu đề, nút trong đầu trang", async () => {
    renderEn(<QuestionEditorView mode="edit" questionId="q-0003" />);
    // Khung chờ tải cũng có lối quay lại: đợi biểu mẫu hiện rồi mới kiểm.
    await screen.findByRole("button", { name: "Save question" });
    const backLink = screen.getByRole("link", { name: "Question details" });
    const heading = screen.getByRole("heading", { level: 1, name: "Edit question" });
    const header = heading.closest('[data-slot="detail-header"]') as HTMLElement;
    expect(header).toContainElement(backLink);
    expect(backLink).toHaveAttribute("href", "/questions/q-0003");
    expect(backLink.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const actions = header.querySelector('[data-slot="page-actions"]') as HTMLElement;
    expect(within(actions).getByRole("button", { name: "Save question" })).toBeInTheDocument();
    expect(within(actions).getByRole("button", { name: "Cancel" })).toBeInTheDocument();
  });

  it("UNI-2878: đầu trang dính trên cùng khi cuộn và giữ cả hàng nút (Đổi dạng câu, Dán từ Word, Hủy, Lưu nháp)", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    // Khung chờ tải cũng có đầu trang (không dính): đợi biểu mẫu hiện rồi mới kiểm.
    await screen.findByRole("button", { name: "Save draft" });
    const heading = screen.getByRole("heading", { level: 1, name: "Create question" });
    const header = heading.closest('[data-slot="detail-header"]') as HTMLElement;
    // `top` âm cho lối quay lại cuộn đi, hàng tiêu đề và nút ở lại; nền che chữ cuộn bên dưới.
    expect(header).toHaveClass("sticky", "-top-9", "z-20", "bg-page/95");
    const actions = header.querySelector('[data-slot="page-actions"]') as HTMLElement;
    expect(header).toContainElement(actions);
    for (const name of ["Change type", "Paste from Word", "Cancel", "Save draft"]) {
      expect(within(actions).getByRole(name === "Change type" ? "link" : "button", { name })).toBeInTheDocument();
    }
  });

  it("UNI-2825: trang tạo có lối quay lại Ngân hàng câu hỏi ở trên tiêu đề", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    const backLink = await screen.findByRole("link", { name: "Question bank" });
    const heading = screen.getByRole("heading", { level: 1, name: "Create question" });
    const header = heading.closest('[data-slot="detail-header"]') as HTMLElement;
    expect(header).toContainElement(backLink);
    expect(backLink).toHaveAttribute("href", "/questions");
    expect(backLink.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("lưu câu Sẵn sàng tạo bản Nháp mới rồi mở trang chi tiết", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="edit" questionId="q-0003" />);
    const content = await screen.findByRole("textbox", { name: /Question text/ });
    await user.type(content, " (edited)");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await vi.waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/questions/q-0003"));
    // Câu đang phục vụ giữ trạng thái và bản phục vụ; bản Nháp mới là `workingVersion` (spec 9.2).
    expect(fakeQuestions.require("q-0003")).toMatchObject({ status: "READY", version: 2, workingVersion: 3 });
  });

  it("câu không tồn tại thì báo không tìm thấy", async () => {
    renderEn(<QuestionEditorView mode="edit" questionId="q-missing" />);
    expect(await screen.findByText("Question not found")).toBeInTheDocument();
  });
});
