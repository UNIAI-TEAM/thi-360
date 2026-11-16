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

function seedCloze() {
  return seedQuestion("CLOZE", (values) => {
    values.content = "Hà Nội là ___ của ___.";
    values.body.blanks = [
      { id: "b1", answers: [{ id: "a1", value: "thủ đô" }], points: "1" },
      { id: "b2", answers: [{ id: "a2", value: "Việt Nam" }, { id: "a3", value: "nước ta" }], points: "1,5" },
    ];
  });
}

describe("trinh soan Dien khuyet", () => {
  it("moi o co diem rieng, tong diem cau tu tinh va chi doc", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const total = await screen.findByRole("textbox", { name: /^Points \(calculated\)/ });
    expect(total).toHaveAttribute("readonly");
    expect(total).toHaveValue("2.5");
    // Điền khuyết không có ô Điểm nhập tay: điểm của câu là tổng điểm các ô.
    expect(screen.queryByRole("textbox", { name: /^Points$/ })).not.toBeInTheDocument();

    const second = screen.getByRole("textbox", { name: "Points of blank 2" });
    expect(second).toHaveValue("1,5");
    await user.clear(second);
    await user.type(second, "2");
    expect(total).toHaveValue("3");

    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    const saved = storedQuestion(id);
    expect(saved.points).toBe(3);
    expect(saved.body).toMatchObject({
      type: "CLOZE",
      passage: "Hà Nội là [[b1]] của [[b2]].",
      blanks: [
        { id: "b1", answers: ["thủ đô"], points: 1 },
        { id: "b2", answers: ["Việt Nam", "nước ta"], points: 2 },
      ],
    });
  });

  it("them o chen cho trong vao de bai, xoa o bo cho trong tuong ung", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const section = (await screen.findByRole("heading", { name: "Answers for each blank" })).closest("section");
    if (!section) throw new Error("no blanks section");
    const text = screen.getByRole("textbox", { name: /^Question text/ });
    expect(within(section).getByText("The text has 2 gaps ___ and 2 answer blanks.")).toBeInTheDocument();

    await user.click(within(section).getByRole("button", { name: "Add blank" }));
    expect(within(section).getByText("The text has 3 gaps ___ and 3 answer blanks.")).toBeInTheDocument();
    expect((text as HTMLTextAreaElement).value.split("___")).toHaveLength(4);
    // Ô mới mang điểm mặc định 1 và đã vào tổng điểm.
    expect(screen.getByRole("textbox", { name: "Points of blank 3" })).toHaveValue("1");
    expect(screen.getByRole("textbox", { name: /^Points \(calculated\)/ })).toHaveValue("3.5");

    await user.click(within(section).getByRole("button", { name: "Delete blank 1" }));
    expect(within(section).getByText("The text has 2 gaps ___ and 2 answer blanks.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Points \(calculated\)/ })).toHaveValue("2.5");
  });

  it("diem o khong hop le, thieu dap an hoac lech so cho trong thi chan luu va chi dung o loi", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const first = await screen.findByRole("textbox", { name: "Points of blank 1" });
    await user.clear(first);
    await user.type(first, "0");
    await user.clear(screen.getByRole("textbox", { name: "Answer 1 for blank 1" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));

    expect(await screen.findByText("Blank points must be a number greater than 0.")).toBeInTheDocument();
    expect(screen.getByText("Enter at least one accepted answer.")).toBeInTheDocument();
    expect(first).toHaveAttribute("aria-invalid", "true");
    expect(navigation.push).not.toHaveBeenCalled();

    // Xóa chỗ trống trong đề bài bằng tay làm số chỗ trống lệch số ô.
    await user.clear(first);
    await user.type(first, "1");
    await user.type(screen.getByRole("textbox", { name: "Answer 1 for blank 1" }), "thủ đô");
    const text = screen.getByRole("textbox", { name: /^Question text/ });
    await user.clear(text);
    await user.type(text, "Chỉ còn một chỗ trống ___ thôi.");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(
      await screen.findByText("The number of ___ gaps in the text must equal the number of answer blanks."),
    ).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });
});
