import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { emptyChild, type EditorChild } from "../editor-form";
import { apiReadiness } from "./api-fixtures";
import { fakeQuestions, renderEn, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

const KEEP = "Keep option order";
const WARNING = /This option refers to other options/;
const warning = (path: string) => ({ code: "OPTION_REFERS_TO_OTHERS", path });

const keepCheckbox = () => screen.getByRole("checkbox", { name: KEEP });

async function save(id: string) {
  await userEvent.click(screen.getByRole("button", { name: "Save question" }));
  await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
}

/** Câu một đáp án với phương án B nhắc tới phương án khác. */
async function seedSingle(keep = false) {
  return seedQuestion("SINGLE_CHOICE", (values) => {
    values.body.keepOptionOrder = keep;
    values.body.options = values.body.options.map((option, index) => ({
      ...option,
      id: `saved-${index}`,
      content: index === 1 ? "Both A and B" : `P${index + 1}`,
      correct: index === 1,
    }));
  });
}

function choiceChild(): EditorChild {
  const child = emptyChild("SINGLE_CHOICE");
  child.content = "Child";
  child.body.options = child.body.options.map((option, index) => ({
    ...option,
    id: `child-saved-${index}`,
    content: index === 2 ? "All of the above" : `C${index + 1}`,
    correct: index === 0,
  }));
  return child;
}

describe("ô Giữ thứ tự phương án", () => {
  it("mặc định không chọn; chọn rồi lưu ghi shuffleOptions = false", async () => {
    const user = userEvent.setup();
    const id = await seedSingle();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("textbox", { name: "Option A text" });
    expect(keepCheckbox()).not.toBeChecked();
    expect(screen.getByText(/Turn on when an option refers to another option/)).toBeInTheDocument();

    await user.click(keepCheckbox());
    expect(keepCheckbox()).toBeChecked();
    await save(id);
    expect(storedQuestion(id).body).toMatchObject({ type: "SINGLE_CHOICE", shuffleOptions: false });
  });

  it("mở lại câu đã giữ thứ tự: ô được chọn; bỏ chọn rồi lưu thì trở về đảo (không còn false)", async () => {
    const user = userEvent.setup();
    const id = await seedSingle(true);
    expect(storedQuestion(id).body).toMatchObject({ shuffleOptions: false });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("textbox", { name: "Option A text" });
    expect(keepCheckbox()).toBeChecked();

    await user.click(keepCheckbox());
    await save(id);
    expect(storedQuestion(id).body).not.toHaveProperty("shuffleOptions", false);
  });

  it("Nhiều đáp án có ô này và ghi cờ", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("MULTIPLE_CHOICE", (values) => {
      values.body.options = values.body.options.map((option, index) => ({
        ...option,
        id: `saved-${index}`,
        content: `P${index + 1}`,
        correct: index < 2,
      }));
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("textbox", { name: "Option A text" });
    await user.click(keepCheckbox());
    await save(id);
    expect(storedQuestion(id).body).toMatchObject({ type: "MULTIPLE_CHOICE", shuffleOptions: false });
  });

  it("Đúng/Sai không có ô này", async () => {
    renderEn(<QuestionEditorView mode="create" type="TRUE_FALSE" />);
    await screen.findByRole("textbox", { name: "Statement a text" });
    expect(screen.queryByRole("checkbox", { name: KEEP })).toBeNull();
  });

  it("câu con Một đáp án của câu chùm có ô này và ghi cờ vào câu con", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = "<p>Passage</p>";
      values.children = [choiceChild(), choiceChild()];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const toggles = await screen.findAllByRole("button", { name: /^Open sub-question \d to edit/ });
    await user.click(toggles[0]!);
    await user.click(await screen.findByRole("checkbox", { name: KEEP }));
    await save(id);
    const stored = storedQuestion(id);
    expect(stored.children[0]!.body).toMatchObject({ shuffleOptions: false });
    expect(stored.children[1]!.body).not.toHaveProperty("shuffleOptions", false);
  });
});

describe("cảnh báo phương án nhắc tới phương án khác", () => {
  it("hiện ngay dưới đúng phương án gây cảnh báo, không hiện ở phương án khác", async () => {
    const id = await seedSingle();
    fakeQuestions.server().setReadiness(id, apiReadiness(true, [], [warning("body.options[1].content")]));
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const warned = (await screen.findByRole("textbox", { name: "Option B text" })).closest("li")!;
    expect(await within(warned).findByText(WARNING)).toBeInTheDocument();
    expect(within(warned).getByRole("button", { name: "Keep order" })).toBeInTheDocument();
    const clean = screen.getByRole("textbox", { name: "Option A text" }).closest("li")!;
    expect(within(clean).queryByText(WARNING)).toBeNull();
  });

  it("nút Giữ thứ tự bật cờ, cảnh báo biến mất và lưu ghi shuffleOptions = false", async () => {
    const user = userEvent.setup();
    const id = await seedSingle();
    fakeQuestions.server().setReadiness(id, apiReadiness(true, [], [warning("body.options[1].content")]));
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const warned = (await screen.findByRole("textbox", { name: "Option B text" })).closest("li")!;
    await user.click(await within(warned).findByRole("button", { name: "Keep order" }));
    expect(keepCheckbox()).toBeChecked();
    expect(within(warned).queryByText(WARNING)).toBeNull();

    await save(id);
    expect(storedQuestion(id).body).toMatchObject({ shuffleOptions: false });
  });

  it("câu con: cảnh báo theo đường dẫn body.children[j]… hiện ở đúng câu con và phương án", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = "<p>Passage</p>";
      values.children = [choiceChild(), choiceChild()];
    });
    fakeQuestions.server().setReadiness(id, apiReadiness(true, [], [warning("body.children[1].body.options[2].content")]));
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const toggles = await screen.findAllByRole("button", { name: /^Open sub-question \d to edit/ });
    await user.click(toggles[0]!);
    await user.click(toggles[1]!);
    const options = await screen.findAllByRole("textbox", { name: "Option C text" });
    const first = options[0]!.closest("li")!;
    const second = options[1]!.closest("li")!;
    expect(within(first).queryByText(WARNING)).toBeNull();
    expect(await within(second).findByText(WARNING)).toBeInTheDocument();
  });

  it("đã bật giữ thứ tự thì không hiện cảnh báo", async () => {
    const id = await seedSingle(true);
    fakeQuestions.server().setReadiness(id, apiReadiness(true, [], [warning("body.options[1].content")]));
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("textbox", { name: "Option B text" });
    await waitFor(() => expect(keepCheckbox()).toBeChecked());
    expect(screen.queryByText(WARNING)).toBeNull();
  });
});
