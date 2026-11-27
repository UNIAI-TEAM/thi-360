import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { renderEn, rich, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
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
    expect(second).toHaveValue("1.5");
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
    expect(rich(text).text.split("___")).toHaveLength(4);
    // Ô mới mang điểm mặc định 1 và đã vào tổng điểm.
    expect(screen.getByRole("textbox", { name: "Points of blank 3" })).toHaveValue("1");
    expect(screen.getByRole("textbox", { name: /^Points \(calculated\)/ })).toHaveValue("3.5");

    await user.click(within(section).getByRole("button", { name: "Delete blank 1" }));
    expect(within(section).getByText("The text has 2 gaps ___ and 2 answer blanks.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Points \(calculated\)/ })).toHaveValue("2.5");
  });

  it("đề bài là HTML: chỗ trống mới nằm trong đoạn văn cuối, không rơi ra ngoài thẻ", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const text = await screen.findByRole("textbox", { name: /^Question text/ });
    rich(text).set("<p>Hà Nội là ___ của ___.</p>");

    await user.click(screen.getByRole("button", { name: "Add blank" }));
    expect(rich(text).text.split("___")).toHaveLength(4);
    expect(rich(text).html).toMatch(/^<p>.*___\s*<\/p>$/);
    expect(rich(text).html.match(/<p>/g)).toHaveLength(1);
  });

  it("chỗ trống mới chèn đúng chỗ con trỏ giữa câu và dòng đáp án mới nằm đúng thứ tự", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const text = await screen.findByRole("textbox", { name: /^Question text/ });
    await rich(text).placeCaretAfter("Hà Nội ");

    await user.click(screen.getByRole("button", { name: "Add blank" }));

    expect(rich(text).text).toBe("Hà Nội ___ là ___ của ___.");
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 1" })).toHaveValue("");
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 2" })).toHaveValue("thủ đô");
  });

  it("chưa từng focus vào đề bài thì chỗ trống mới nối vào cuối", async () => {
    const user = userEvent.setup();
    const id = await seedCloze();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const text = await screen.findByRole("textbox", { name: /^Question text/ });

    await user.click(screen.getByRole("button", { name: "Add blank" }));

    expect(rich(text).text).toBe("Hà Nội là ___ của ___. ___");
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 3" })).toHaveValue("");
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

  });

  it("dữ liệu cũ có số ___ lệch số ô thì vẫn chặn lưu, mở biểu mẫu không tự bỏ ô nào", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("CLOZE", (values) => {
      values.content = "Chỉ còn một chỗ trống ___ thôi.";
      values.body.blanks = [
        { id: "b1", answers: [{ id: "a1", value: "x" }], points: "1" },
        { id: "b2", answers: [{ id: "a2", value: "y" }], points: "1" },
      ];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    expect(await screen.findByText("The text has 1 gap ___ and 2 answer blanks.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 2" })).toHaveValue("y");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(
      await screen.findByText("The number of ___ gaps in the text must equal the number of answer blanks."),
    ).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("gõ trực tiếp ___ vào Đề bài tự sinh đủ ô đáp án tương ứng bên dưới (UNI-2833)", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="CLOZE" />);
    const text = await screen.findByRole("textbox", { name: /^Question text/ });

    expect(screen.queryByRole("textbox", { name: "Answer 1 for blank 1" })).not.toBeInTheDocument();

    rich(text).set("<p>anh không biết ___</p>");
    const blank1 = await screen.findByRole("textbox", { name: "Answer 1 for blank 1" });
    expect(blank1).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Points of blank 1" })).toHaveValue("1");
    expect(screen.getByText("The text has 1 gap ___ and 1 answer blank.")).toBeInTheDocument();

    // Nhập đáp án ô 1 rồi dán/gõ thêm chỗ trống thứ hai và thứ ba: tự sinh thêm đúng 2 ô, giữ nguyên đáp án ô 1.
    await user.type(blank1, "gì");
    rich(text).set("<p>anh không biết ___ và ___ lẫn ___</p>");
    expect(await screen.findByRole("textbox", { name: "Answer 1 for blank 2" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 3" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 1" })).toHaveValue("gì");
    expect(screen.getByText("The text has 3 gaps ___ and 3 answer blanks.")).toBeInTheDocument();
  });

  it("gõ thêm ___ ở đầu đề bài trước các ô có sẵn sẽ chèn đúng vị trí đầu, không làm lệch đáp án phía sau", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="CLOZE" />);
    const text = await screen.findByRole("textbox", { name: /^Question text/ });

    rich(text).set("<p>A ___ B ___</p>");
    await user.type(await screen.findByRole("textbox", { name: "Answer 1 for blank 1" }), "x");
    await user.type(screen.getByRole("textbox", { name: "Answer 1 for blank 2" }), "y");

    rich(text).set("<p>___ A ___ B ___</p>");
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Answer 1 for blank 3" })).toBeInTheDocument());
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 1" })).toHaveValue("");
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 2" })).toHaveValue("x");
    expect(screen.getByRole("textbox", { name: "Answer 1 for blank 3" })).toHaveValue("y");
  });

  describe("xóa ___ khỏi Đề bài bỏ đúng ô đáp án tương ứng (UNI-2878)", () => {
    const html = "<p>A ___ B ___ C ___.</p>";

    async function openThreeBlanks() {
      const id = await seedQuestion("CLOZE", (values) => {
        values.content = html;
        values.body.blanks = ["x", "y", "z"].map((value, index) => ({
          id: `b${index + 1}`,
          answers: [{ id: `a${index + 1}`, value }],
          points: "1",
        }));
      });
      renderEn(<QuestionEditorView mode="edit" questionId={id} />);
      const text = await screen.findByRole("textbox", { name: /^Question text/ });
      expect(await screen.findByRole("textbox", { name: "Answer 1 for blank 3" })).toHaveValue("z");
      return text;
    }

    const answers = () =>
      screen.queryAllByRole("textbox", { name: /^Answer 1 for blank \d+$/ }).map((input) => (input as HTMLInputElement).value);

    it("xóa ___ ở giữa bỏ ô giữa, các ô khác giữ đáp án; hoàn tác trả lại ô cùng đáp án", async () => {
      const text = await openThreeBlanks();
      await rich(text).placeCaretAfter("C ");

      rich(text).set("<p>A ___ B  C ___.</p>");
      await waitFor(() => expect(answers()).toEqual(["x", "z"]));
      expect(screen.getByText("The text has 2 gaps ___ and 2 answer blanks.")).toBeInTheDocument();
      expect(screen.getByRole("textbox", { name: /^Points \(calculated\)/ })).toHaveValue("2");
      // Bỏ ô không cướp tiêu điểm khỏi ô Đề bài.
      expect(text.contains(document.activeElement)).toBe(true);

      // Ctrl+Z đưa đề bài về như cũ: ô 2 quay lại đúng chỗ với đáp án cũ.
      rich(text).set(html);
      await waitFor(() => expect(answers()).toEqual(["x", "y", "z"]));
      expect(screen.getByText("The text has 3 gaps ___ and 3 answer blanks.")).toBeInTheDocument();
    });

    it("bôi đen nhiều ___ rồi xóa bỏ đủ các ô đó", async () => {
      const text = await openThreeBlanks();
      rich(text).set("<p>A  C ___.</p>");
      await waitFor(() => expect(answers()).toEqual(["z"]));
      expect(screen.getByText("The text has 1 gap ___ and 1 answer blank.")).toBeInTheDocument();
    });

    it("chọn tất cả rồi xóa thì không còn ô nào", async () => {
      const text = await openThreeBlanks();
      rich(text).set("<p></p>");
      await waitFor(() => expect(answers()).toEqual([]));
      expect(screen.getByText("The text has 0 gaps ___ and 0 answer blanks.")).toBeInTheDocument();
      expect(screen.queryByText("Enter at least one accepted answer.")).not.toBeInTheDocument();
    });

    it("sửa chữ khác trong đề bài không bỏ ô nào", async () => {
      const text = await openThreeBlanks();
      rich(text).set("<p>A ___ Bờ biển ___ C ___ thêm chữ.</p>");
      rich(text).set("<p>A ___ ___ C ___.</p>");
      await waitFor(() => expect(answers()).toEqual(["x", "y", "z"]));
      expect(screen.getByText("The text has 3 gaps ___ and 3 answer blanks.")).toBeInTheDocument();
    });
  });
});
