import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetQuestionHistoryForTests } from "../editor-api.mock";
import { QuestionEditorView } from "../components/question-editor-view";
import { questionStore } from "../mock/store";
import { renderEn, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

afterEach(() => {
  questionStore.reset();
  resetQuestionHistoryForTests();
});

describe("trinh soan Tra loi ngan", () => {
  it("dap an chuan dung dau, them dap an chap nhan va huong dan nhap roi luu dung thu tu", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SHORT_ANSWER", (values) => {
      values.body.blanks = [{ id: "b", answers: [{ id: "a1", value: "0,5" }], points: "1" }];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const section = (await screen.findByRole("heading", { name: "Answer" })).closest("section");
    if (!section) throw new Error("no answer section");
    expect(within(section).getByRole("textbox", { name: "Standard answer" })).toHaveValue("0,5");
    // Chỉ còn một đáp án thì không xóa được: câu phải luôn có đáp án chuẩn.
    expect(within(section).queryByRole("button", { name: /^Remove answer/ })).not.toBeInTheDocument();

    await user.click(within(section).getByRole("button", { name: "Add accepted answer" }));
    const accepted = within(section).getByRole("textbox", { name: "Accepted answer 1" });
    expect(accepted).toHaveFocus();
    await user.type(accepted, "1/2");
    await user.click(within(section).getByRole("button", { name: "Add accepted answer" }));
    await user.type(within(section).getByRole("textbox", { name: "Accepted answer 2" }), "   ");
    await user.type(within(section).getByRole("textbox", { name: "Input hint" }), "Write a decimal comma");

    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    // Đáp án chỉ có khoảng trắng bị bỏ; không tự chấp nhận cách viết tương đương (0,5 và 1/2 là hai đáp án khai báo).
    expect(storedQuestion(id).body).toMatchObject({
      type: "SHORT_ANSWER",
      answers: ["0,5", "1/2"],
      inputHint: "Write a decimal comma",
    });
  });

  it("xoa dap an chap nhan, khong xoa duoc dap an chuan duy nhat", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SHORT_ANSWER", (values) => {
      values.body.blanks = [
        { id: "b", answers: [{ id: "a1", value: "12" }, { id: "a2", value: "mười hai" }], points: "1" },
      ];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("button", { name: "Remove answer 2" }));
    expect(screen.queryByRole("textbox", { name: "Accepted answer 1" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Remove answer/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalled());
    expect(storedQuestion(id).body).toMatchObject({ answers: ["12"], inputHint: null });
  });

  it("thieu dap an chuan thi bao loi tai o va khong luu", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SHORT_ANSWER", (values) => {
      values.body.blanks = [{ id: "b", answers: [{ id: "a1", value: "" }], points: "1" }];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("button", { name: "Save question" }));
    expect(await screen.findByText("Enter at least one accepted answer.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
    expect(storedQuestion(id).body).toMatchObject({ answers: [] });

    await user.type(screen.getByRole("textbox", { name: "Standard answer" }), "42");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({ answers: ["42"] });
  });
});
