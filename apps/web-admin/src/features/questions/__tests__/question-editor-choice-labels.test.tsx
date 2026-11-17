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

const AUTO = "Options taken from the underlined phrases";

const u = (label: string, text: string) => `<u data-label="${label}">${text}</u>`;
const stem = (...phrases: [string, string][]) => `<p>${phrases.map(([label, text]) => u(label, text)).join(" and ")}</p>`;

/** Hộp chọn "tự lấy" chỉ hiện khi câu có cụm gạch chân. */
const autoCheckbox = () => screen.getByRole("checkbox", { name: AUTO });

async function stemField() {
  return rich(await screen.findByRole("textbox", { name: /^Question text/ }));
}

describe("phương án tự lấy theo cụm gạch chân (Trắc nghiệm 1 đáp án)", () => {
  it("câu mới: một cụm chưa bật; cụm thứ hai thì bật, A–B lấy chữ cụm, C–D báo thiếu", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    const field = await stemField();
    expect(screen.queryByRole("checkbox", { name: AUTO })).toBeNull();

    field.set(stem(["A", "has been"]));
    await waitFor(() => expect(autoCheckbox()).not.toBeChecked());
    // Chưa bật: phương án vẫn soạn tay.
    expect(screen.getByRole("textbox", { name: /^Option A text/ })).toBeInTheDocument();

    field.set(stem(["A", "has been"], ["B", "to"]));
    await waitFor(() => expect(autoCheckbox()).toBeChecked());
    const a = screen.getByRole("group", { name: "Option A text" });
    expect(within(a).getByText("has been")).toBeInTheDocument();
    expect(within(screen.getByRole("group", { name: "Option B text" })).getByText("to")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Option A text/ })).toBeNull();
    // Báo thiếu ngay dưới danh sách phương án: C, D chưa có cụm.
    expect(screen.getByText("No underlined phrase for option C, D.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Option C text/ })).toBeInTheDocument();

    // Sửa cụm thì phương án theo kịp.
    field.set(stem(["A", "have been"], ["B", "to"]));
    await waitFor(() => expect(within(screen.getByRole("group", { name: "Option A text" })).getByText("have been")).toBeInTheDocument());
  });

  it("tắt thì phương án soạn tay lại, giữ chữ đã lấy", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    const field = await stemField();
    field.set(stem(["A", "has been"], ["B", "to"]));
    await waitFor(() => expect(autoCheckbox()).toBeChecked());

    await user.click(autoCheckbox());
    expect(autoCheckbox()).not.toBeChecked();
    const optionA = await screen.findByRole("textbox", { name: /^Option A text/ });
    expect(rich(optionA).text).toBe("has been");
    expect(screen.queryByText(/^No underlined phrase for option/)).toBeNull();
  });

  it("báo cụm thừa khi nhiều cụm hơn phương án", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    const field = await stemField();
    field.set(stem(["A", "a1"], ["B", "b1"], ["C", "c1"], ["D", "d1"], ["E", "e1"]));
    await waitFor(() => expect(autoCheckbox()).toBeChecked());
    expect(screen.getByText("Underlined phrase E has no option.")).toBeInTheDocument();
  });

  it("mở câu cũ: bật khi mọi phương án đúng chữ cụm cùng nhãn, tắt khi lệch", async () => {
    const matching = await seedQuestion("SINGLE_CHOICE", (values) => {
      values.content = stem(["A", "has been"], ["B", "to"], ["C", "have always been"], ["D", "sometimes"]);
      values.body.options = ["has been", "to", "have always been", "sometimes"].map((text, index) => ({
        ...values.body.options[index]!,
        id: `saved-${index}`,
        content: `<p>${text}</p>`,
        correct: index === 0,
      }));
    });
    const first = renderEn(<QuestionEditorView mode="edit" questionId={matching} />);
    await waitFor(() => expect(autoCheckbox()).toBeChecked());
    first.unmount();

    const handTyped = await seedQuestion("SINGLE_CHOICE", (values) => {
      values.content = stem(["A", "has been"], ["B", "to"], ["C", "have always been"], ["D", "sometimes"]);
      values.body.options = ["has been", "to", "have always been", "often"].map((text, index) => ({
        ...values.body.options[index]!,
        id: `saved-${index}`,
        content: `<p>${text}</p>`,
        correct: index === 0,
      }));
    });
    renderEn(<QuestionEditorView mode="edit" questionId={handTyped} />);
    await waitFor(() => expect(autoCheckbox()).not.toBeChecked());
    expect(screen.getByRole("textbox", { name: /^Option D text/ })).toBeInTheDocument();
  });

  it("Nhiều đáp án không có tùy chọn này", async () => {
    renderEn(<QuestionEditorView mode="create" type="MULTIPLE_CHOICE" />);
    const field = await stemField();
    field.set(stem(["A", "has been"], ["B", "to"]));
    expect(screen.queryByRole("checkbox", { name: AUTO })).toBeNull();
    expect(screen.getByRole("textbox", { name: /^Option A text/ })).toBeInTheDocument();
  });

  it("lưu: phương án đã lấy là chữ thuần của cụm", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SINGLE_CHOICE", (values) => {
      values.body.options = values.body.options.map((option, index) => ({
        ...option,
        id: `saved-${index}`,
        content: `<p>Phương án ${index + 1}</p>`,
        correct: index === 0,
      }));
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const field = await stemField();
    field.set(stem(["A", "has <strong>been</strong>"], ["B", "to"], ["C", "&amp;"], ["D", "sometimes"]));
    await waitFor(() => expect(autoCheckbox()).not.toBeChecked());
    await user.click(autoCheckbox());
    await waitFor(() => expect(within(screen.getByRole("group", { name: "Option A text" })).getByText("has been")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    const body = storedQuestion(id).body;
    if (body.type !== "SINGLE_CHOICE") throw new Error("not single choice");
    expect(body.options.map((option) => option.content)).toEqual(["<p>has been</p>", "<p>to</p>", "<p>&amp;</p>", "<p>sometimes</p>"]);
  });
});
