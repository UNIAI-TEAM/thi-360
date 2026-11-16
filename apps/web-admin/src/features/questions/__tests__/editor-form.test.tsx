import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { resetQuestionHistoryForTests } from "../editor-api.mock";
import { QuestionEditorView } from "../components/question-editor-view";
import { QuestionTypePicker } from "../components/question-type-picker";
import { questionStore } from "../mock/store";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

function renderEn(ui: ReactElement) {
  return renderWithProviders(ui, { locale: "en", messages: en });
}

beforeEach(() => {
  navigation.push.mockReset();
});

afterEach(() => {
  questionStore.reset();
  resetQuestionHistoryForTests();
});

describe("chọn dạng câu", () => {
  it("chọn một dạng rồi Tiếp tục mở biểu mẫu của dạng đó", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionTypePicker />);
    const group = screen.getByRole("group", { name: "Question type" });
    expect(within(group).getAllByRole("radio")).toHaveLength(7);
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

  it("lưu câu Sẵn sàng tạo bản Nháp mới rồi mở trang chi tiết", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="edit" questionId="q-0003" />);
    const content = await screen.findByRole("textbox", { name: /Question text/ });
    await user.type(content, " (edited)");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await vi.waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/questions/q-0003"));
    expect(questionStore.require("q-0003")).toMatchObject({ version: 3, status: "DRAFT" });
  });

  it("câu không tồn tại thì báo không tìm thấy", async () => {
    renderEn(<QuestionEditorView mode="edit" questionId="q-missing" />);
    expect(await screen.findByText("Question not found")).toBeInTheDocument();
  });
});
