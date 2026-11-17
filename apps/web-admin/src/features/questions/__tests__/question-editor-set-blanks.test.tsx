import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { emptyChild, type EditorChild } from "../editor-form";
import { renderEn, rich, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

const blank = (n: number) => `<span class="blank" data-blank="${n}"></span>`;

function choiceChild(content: string, correctIndex = 0): EditorChild {
  const child = emptyChild("SINGLE_CHOICE");
  child.content = content;
  child.body.options = child.body.options.map((option, index) => ({ ...option, content: `P${index + 1}`, correct: index === correctIndex }));
  return child;
}

/** Ngữ liệu có chỗ trống 1 và 2; ba câu con (câu 3 chưa có chỗ trống), đáp án đúng khác nhau để nhận ra từng câu. */
async function seedSet(start = "21") {
  return seedQuestion("QUESTION_SET", (values) => {
    values.content = `<p>w1 ${blank(1)} w2 ${blank(2)} end</p>`;
    values.body.blankNumberStart = start;
    values.children = [choiceChild("Câu 1", 0), choiceChild("Câu 2", 1), choiceChild("Câu 3", 2)];
  });
}

const passageHtml = () => rich(screen.getByRole("textbox", { name: /^Shared passage/ })).html;
const table = () => screen.getByRole("table", { name: "Blanks and sub-questions" });
const rowsOf = () => within(table()).getAllByRole("row").slice(1);

async function save(id: string) {
  await userEvent.click(screen.getByRole("button", { name: "Save question" }));
  await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
}

describe("câu chùm: Đánh số từ và bảng Chỗ trống và câu con", () => {
  it("hiện ô Đánh số từ, mỗi chỗ trống với câu con, dạng, đáp án; số hiển thị = Đánh số từ + n − 1", async () => {
    const id = await seedSet("21");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const start = await screen.findByRole("textbox", { name: "Number from" });
    expect(start).toHaveValue("21");

    const [first, second] = rowsOf();
    expect(within(first!).getByText("(21) ____")).toBeInTheDocument();
    expect(within(first!).getByText(/Sub-question 1 · Single choice/)).toBeInTheDocument();
    expect(within(first!).getByText("Answer: A")).toBeInTheDocument();
    expect(within(second!).getByText("(22) ____")).toBeInTheDocument();
    expect(within(second!).getByText("Answer: B")).toBeInTheDocument();
    // Câu con không có chỗ trống là hợp lệ: chỉ ghi chú.
    expect(screen.getByText("Sub-questions without a blank: 3.")).toBeInTheDocument();

    // Đổi Đánh số từ đổi số hiển thị.
    const user = userEvent.setup();
    await user.clear(start);
    await user.type(start, "31");
    await waitFor(() => expect(within(rowsOf()[0]!).getByText("(31) ____")).toBeInTheDocument());
    expect(within(rowsOf()[1]!).getByText("(32) ____")).toBeInTheDocument();
    // Ngữ liệu trong trình soạn cũng hiện số mới (ED-VG F1, ngữ liệu ở ô soạn).
    const passage = screen.getByRole("textbox", { name: /^Shared passage/ });
    await waitFor(() => expect([...passage.querySelectorAll(".rich-text-blank")].map((node) => node.textContent)).toEqual(["(31) ____", "(32) ____"]));

    await save(id);
    expect(storedQuestion(id).body).toMatchObject({ type: "QUESTION_SET", blankNumberStart: 31 });
  });

  it("Đánh số từ ngoài 1-200 bị chặn khi lưu và báo lỗi ở ô", async () => {
    const user = userEvent.setup();
    const id = await seedSet("21");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const start = await screen.findByRole("textbox", { name: "Number from" });
    await user.clear(start);
    await user.type(start, "201");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("Enter a whole number from 1 to 200.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
    expect(storedQuestion(id).body).toMatchObject({ blankNumberStart: 21 });
  });

  it("đổi thứ tự câu con đánh lại data-blank; lưu giữ khớp", async () => {
    const user = userEvent.setup();
    const id = await seedSet("1");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });

    // Câu con 1 xuống vị trí 2: chỗ trống của nó đi theo (1 → 2), chỗ trống của câu 2 thành 1.
    await user.click(screen.getByRole("button", { name: "Move sub-question 1 down" }));
    await waitFor(() => expect(passageHtml()).toBe(`<p>w1 ${blank(2)} w2 ${blank(1)} end</p>`));
    // Hàng của bảng theo số chỗ trống; câu con 1 giờ là câu có đáp án B (câu cũ 2).
    await waitFor(() => expect(within(rowsOf()[0]!).getByText("Answer: B")).toBeInTheDocument());
    expect(within(rowsOf()[1]!).getByText("Answer: A")).toBeInTheDocument();

    await save(id);
    expect(storedQuestion(id).content).toBe(`<p>w1 ${blank(2)} w2 ${blank(1)} end</p>`);
    expect(storedQuestion(id).children.map((child) => child.content)).toEqual(["Câu 2", "Câu 1", "Câu 3"]);
  });

  it("xóa câu con k xóa chỗ trống của nó và giảm 1 các số lớn hơn k", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = `<p>w1 ${blank(1)} w2 ${blank(2)} w3 ${blank(3)} end</p>`;
      values.children = [choiceChild("Câu 1", 0), choiceChild("Câu 2", 1), choiceChild("Câu 3", 2)];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });

    await user.click(screen.getByRole("button", { name: "Delete sub-question 2" }));
    // Hai khoảng trắng liền nhau do chỗ trống bị xóa được trình soạn gộp thành một.
    await waitFor(() => expect(passageHtml()).toBe(`<p>w1 ${blank(1)} w2 w3 ${blank(2)} end</p>`));
    expect(rowsOf()).toHaveLength(2);
  });

  it("HTML ngữ liệu đã lưu không đúng dạng chuẩn: bảng đọc được chỗ trống; đổi thứ tự và xóa câu con vẫn đánh lại số", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = `<p>w1 <span data-blank="1" class="blank"></span> w2 <span class="blank" data-blank = "2" ></span> w3 ${blank(3)} end</p>`;
      values.children = [choiceChild("Câu 1", 0), choiceChild("Câu 2", 1), choiceChild("Câu 3", 2)];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });
    expect(rowsOf()).toHaveLength(3);

    await user.click(screen.getByRole("button", { name: "Move sub-question 1 down" }));
    await waitFor(() => expect(passageHtml()).toBe(`<p>w1 ${blank(2)} w2 ${blank(1)} w3 ${blank(3)} end</p>`));
    await user.click(screen.getByRole("button", { name: "Delete sub-question 2" }));
    await waitFor(() => expect(passageHtml()).toBe(`<p>w1 w2 ${blank(1)} w3 ${blank(2)} end</p>`));
  });

  it("nút sửa câu con trong bảng mở câu con và chuyển focus vào ô đầu tiên của nó", async () => {
    const user = userEvent.setup();
    const id = await seedSet("1");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });

    await user.click(within(table()).getByRole("button", { name: "Edit sub-question 2" }));
    const card = screen.getByRole("heading", { name: "Question 2" }).closest("li");
    await waitFor(() => expect(card).toContainElement(document.activeElement as HTMLElement));
    expect(document.activeElement).toHaveAttribute("contenteditable", "true");
  });

  it("thêm câu con không tự thêm chỗ trống; xóa chỗ trống không đổi câu con", async () => {
    const user = userEvent.setup();
    const id = await seedSet("1");
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });
    const before = passageHtml();

    await user.click(screen.getByRole("button", { name: "Single choice" }));
    expect(passageHtml()).toBe(before);
    expect(rowsOf()).toHaveLength(2);
    expect(screen.getByText("Sub-questions without a blank: 3, 4.")).toBeInTheDocument();

    // Xóa chỗ trống khỏi ngữ liệu: câu con vẫn còn.
    rich(screen.getByRole("textbox", { name: /^Shared passage/ })).set("<p>w1 w2 end</p>");
    await waitFor(() => expect(screen.getByText(/The passage has no blanks yet/)).toBeInTheDocument());
    expect(screen.getAllByRole("heading", { name: /^Question \d$/ })).toHaveLength(4);
  });

  it("chỗ trống trỏ tới câu con không có hoặc trùng số được đánh dấu trong bảng", async () => {
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = `<p>${blank(1)} ${blank(1)} ${blank(5)}</p>`;
      values.children = [choiceChild("Câu 1"), choiceChild("Câu 2")];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findByRole("table", { name: "Blanks and sub-questions" });
    expect(screen.getAllByText("Duplicate number")).toHaveLength(2);
    expect(screen.getByText("No sub-question 5")).toBeInTheDocument();
  });
});
