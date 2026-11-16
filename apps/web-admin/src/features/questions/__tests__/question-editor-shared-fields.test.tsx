import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getQuestion } from "../api";
import { resetQuestionHistoryForTests } from "../editor-api.mock";
import { emptyChild, toEditorValues, type EditorChild } from "../editor-form";
import { QuestionEditorView } from "../components/question-editor-view";
import { questionStore } from "../mock/store";
import { renderEn, rich, seedQuestion, storedQuestion } from "./editor-test-kit";

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

function seedSingleChoice() {
  return seedQuestion("SINGLE_CHOICE", (values) => {
    values.body.options = values.body.options.map((option, index) => ({
      ...option,
      content: `Phương án ${index + 1}`,
      correct: index === 0,
    }));
  });
}

function choiceChild(content: string): EditorChild {
  const child = emptyChild("SINGLE_CHOICE");
  child.content = content;
  child.body.options = child.body.options.map((option, index) => ({ ...option, content: `P${index + 1}`, correct: index === 0 }));
  return child;
}

describe("truong dung chung: dang bai, loi sai thuong gap", () => {
  it("chon dang bai va ghi loi sai thuong gap roi luu", async () => {
    const user = userEvent.setup();
    const id = await seedSingleChoice();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const taskType = await screen.findByRole("combobox", { name: "Task type" });
    expect(taskType).toHaveTextContent("None");

    await user.click(taskType);
    await user.click(await screen.findByRole("option", { name: "Calculation" }));
    rich(screen.getByRole("textbox", { name: /^Common mistakes/ })).set("<p>Nhầm dấu khi chuyển vế</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id)).toMatchObject({ taskTypeCode: "CALCULATION", commonMistakes: "<p>Nhầm dấu khi chuyển vế</p>" });

    // Chọn lại "None" bỏ dạng bài.
    await user.click(screen.getByRole("combobox", { name: "Task type" }));
    await user.click(await screen.findByRole("option", { name: "None" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledTimes(2));
    expect(storedQuestion(id).taskTypeCode).toBeNull();
  });
});

describe("truong dung chung: tep dinh kem", () => {
  it("tai len tep hop le, bo qua tep sai loai hoac qua 20 MB kem ly do, xoa tep", async () => {
    const user = userEvent.setup({ applyAccept: false });
    const id = await seedSingleChoice();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const input = await screen.findByLabelText("Choose attachments");
    expect(screen.getByText("No attachments.")).toBeInTheDocument();

    const big = new File(["x"], "big.pdf", { type: "application/pdf" });
    Object.defineProperty(big, "size", { value: 21 * 1024 * 1024 });
    await user.upload(input, [
      new File(["pdf"], "plan.pdf", { type: "application/pdf" }),
      new File(["exe"], "virus.exe", { type: "application/octet-stream" }),
      big,
    ]);
    expect(await screen.findByText("plan.pdf")).toBeInTheDocument();
    expect(screen.getByText("virus.exe: this file type is not supported.")).toBeInTheDocument();
    expect(screen.getByText("big.pdf: the file is larger than 20 MB.")).toBeInTheDocument();
    expect(screen.queryByText("virus.exe")).not.toBeInTheDocument();
    expect(screen.queryByText("No attachments.")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).attachmentIds).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "Remove file plan.pdf" }));
    expect(screen.getByText("No attachments.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledTimes(2));
    expect(storedQuestion(id).attachmentIds).toEqual([]);
  });
});

describe("cau con cua bo cau hoi sua phan loai rieng", () => {
  async function seedSet() {
    const other = toEditorValues(await getQuestion("q-0013")).knowledge;
    return seedQuestion("QUESTION_SET", (values) => {
      values.content = "Ngữ liệu chung";
      const first = choiceChild("Câu 1");
      first.gradeCode = "GRADE_12";
      first.knowledgeOverride = true;
      first.knowledge = other;
      values.children = [first, choiceChild("Câu 2")];
    });
  }

  async function openChild(number: number) {
    await userEvent.click(await screen.findByRole("button", { name: `Open sub-question ${number} to edit` }));
    const card = screen.getByRole("heading", { name: `Question ${number}` }).closest("li");
    if (!card) throw new Error(`no card ${number}`);
    return within(card);
  }

  it("mac dinh ke thua cua bo, ghi de rieng duoc nhan ra khi mo lai va luu dung", async () => {
    const user = userEvent.setup();
    const id = await seedSet();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    const first = await openChild(1);
    expect(first.getByRole("combobox", { name: "Grade" })).toHaveTextContent("Grade 12");
    expect(first.getByRole("combobox", { name: "Difficulty" })).toHaveTextContent("Same as the set: Recognition");
    expect(first.getByText("Own")).toBeInTheDocument();
    expect(first.getByText("Phương trình dao động", { exact: false })).toBeInTheDocument();

    const second = await openChild(2);
    expect(second.getByRole("combobox", { name: "Grade" })).toHaveTextContent("Same as the set: Grade 11");
    expect(second.getByText("Same as the set")).toBeInTheDocument();
    expect(second.getByText("Giới hạn hữu hạn của dãy số", { exact: false })).toBeInTheDocument();

    await user.click(second.getByRole("combobox", { name: "Difficulty" }));
    await user.click(await screen.findByRole("option", { name: "Comprehension" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));

    const [savedFirst, savedSecond] = storedQuestion(id).children;
    expect(savedFirst).toMatchObject({
      gradeCode: "GRADE_12",
      difficultyCode: "RECOGNITION",
      knowledge: [{ nodeId: "kn-2L1-1-1" }],
    });
    expect(savedSecond).toMatchObject({
      gradeCode: "GRADE_11",
      difficultyCode: "COMPREHENSION",
      knowledge: [{ nodeId: "kn-1D4-1-1" }],
    });
  });

  it("dung lai kien thuc cua bo thi bo ghi de rieng", async () => {
    const user = userEvent.setup();
    const id = await seedSet();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const first = await openChild(1);
    await user.click(first.getByRole("button", { name: "Use the set's knowledge" }));
    expect(first.getByText("Same as the set")).toBeInTheDocument();
    expect(first.queryByRole("button", { name: "Use the set's knowledge" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).children[0]?.knowledge).toEqual(storedQuestion(id).knowledge);
  });
});
