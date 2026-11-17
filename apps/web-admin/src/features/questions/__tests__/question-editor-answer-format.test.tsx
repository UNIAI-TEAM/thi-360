import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { emptyBody, emptyChild, editorToBody } from "../editor-form";
import type { QuestionSnapshot } from "../editor-types";
import { diffSnapshots, toSnapshot } from "../editor-versioning";
import type { AnswerFormat } from "../types";
import { fromWireBody } from "../wire-body";
import { renderEn, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

function shortAnswer(answerFormat?: string) {
  const body = emptyBody("SHORT_ANSWER");
  body.blanks = [{ id: "b", answers: [{ id: "a1", value: "12" }], points: "1" }];
  if (answerFormat !== undefined) body.answerFormat = answerFormat;
  return body;
}

describe("dinh dang dap an cua cau Tra loi ngan (H11)", () => {
  it("chi hien o cau Tra loi ngan, khong hien o dang khac", async () => {
    const shortId = await seedQuestion("SHORT_ANSWER", (values) => {
      values.body = shortAnswer();
    });
    const { unmount } = renderEn(<QuestionEditorView mode="edit" questionId={shortId} />);
    expect(await screen.findByRole("combobox", { name: "Answer format" })).toHaveTextContent("None");
    unmount();

    const choiceId = await seedQuestion("SINGLE_CHOICE", (values) => {
      values.body.options = values.body.options.map((option, index) => ({ ...option, content: `P${index}`, correct: index === 0 }));
    });
    renderEn(<QuestionEditorView mode="edit" questionId={choiceId} />);
    await screen.findByRole("button", { name: "Save question" });
    expect(screen.queryByRole("combobox", { name: "Answer format" })).not.toBeInTheDocument();
  });

  it("chon dinh dang thi luu vao than yeu cau, mo lai thay dung, chon None thi bo", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("SHORT_ANSWER", (values) => {
      values.body = shortAnswer();
    });
    const first = renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    await user.click(await screen.findByRole("combobox", { name: "Answer format" }));
    await user.click(await screen.findByRole("option", { name: "Chemical formula" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({ type: "SHORT_ANSWER", answers: ["12"], answerFormat: "CHEM_FORMULA" });
    first.unmount();

    // Mở lại: ô hiện định dạng đã lưu; chọn None rồi lưu thì định dạng bị bỏ.
    navigation.push.mockReset();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    const field = await screen.findByRole("combobox", { name: "Answer format" });
    await waitFor(() => expect(field).toHaveTextContent("Chemical formula"));
    await user.click(field);
    await user.click(await screen.findByRole("option", { name: "None" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).not.toHaveProperty("answerFormat");
  });

  it("cau con Tra loi ngan cua cau chum co o chon dinh dang rieng va luu theo tung cau con", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = "<p>Passage</p>";
      const child = emptyChild("SHORT_ANSWER");
      child.content = "Child 1";
      child.body = shortAnswer("DECIMAL");
      const other = emptyChild("SHORT_ANSWER");
      other.content = "Child 2";
      other.body = shortAnswer();
      values.children = [child, other];
    });
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);

    await user.click(await screen.findByRole("button", { name: "Open sub-question 1 to edit" }));
    await user.click(await screen.findByRole("button", { name: "Open sub-question 2 to edit" }));
    const formats = await screen.findAllByRole("combobox", { name: "Answer format" });
    expect(formats).toHaveLength(2);
    expect(formats[0]).toHaveTextContent("Decimal");
    expect(formats[1]).toHaveTextContent("None");

    await user.click(formats[1]!);
    await user.click(await screen.findByRole("option", { name: "Fraction" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));

    const saved = storedQuestion(id);
    expect(saved.children.map((child) => (child.body.type === "SHORT_ANSWER" ? child.body.answerFormat : "x"))).toEqual([
      "DECIMAL",
      "FRACTION",
    ]);
  });
});

const BASE_SNAPSHOT: QuestionSnapshot = {
  subjectCode: "S",
  gradeCode: "G",
  difficultyCode: "D",
  competencyCode: null,
  taskTypeCode: null,
  points: 1,
  knowledge: [],
  tagCodes: [],
  content: "c",
  body: { type: "SHORT_ANSWER", answers: [], inputHint: null },
  solution: null,
  commonMistakes: null,
  attachmentIds: [],
  children: [],
};

describe("answerFormat: chuyen doi bieu mau va tren day", () => {
  it("trong hoac gia tri la thi bo truong; gia tri hop le thi giu nguyen", () => {
    expect(editorToBody("SHORT_ANSWER", shortAnswer(), "")).not.toHaveProperty("answerFormat");
    expect(editorToBody("SHORT_ANSWER", shortAnswer("NOT_A_FORMAT"), "")).not.toHaveProperty("answerFormat");
    expect(editorToBody("SHORT_ANSWER", shortAnswer("INTEGER"), "")).toMatchObject({ answerFormat: "INTEGER" });
  });

  it("doc tu day: gia tri chua biet hoac thieu la chua dat; khong lam hong bieu mau", () => {
    expect(fromWireBody({ type: "SHORT_ANSWER", answers: ["1"] })).not.toHaveProperty("answerFormat");
    expect(fromWireBody({ type: "SHORT_ANSWER", answers: ["1"], answerFormat: "FUTURE_FORMAT" })).not.toHaveProperty(
      "answerFormat",
    );
    expect(fromWireBody({ type: "SHORT_ANSWER", answers: ["1"], answerFormat: "FRACTION" })).toMatchObject({
      answerFormat: "FRACTION",
    });
  });

  it("lich su phien ban: doi dinh dang la mot thay doi cua truong Dap an; khong dat va null la nhu nhau", () => {
    const snapshot = (answerFormat?: AnswerFormat | null) =>
      toSnapshot({
        ...BASE_SNAPSHOT,
        body: { type: "SHORT_ANSWER", answers: ["1"], inputHint: null, ...(answerFormat === undefined ? {} : { answerFormat }) },
      });
    expect(diffSnapshots(snapshot(), snapshot(null))).toEqual([]);
    expect(diffSnapshots(snapshot(), snapshot("DECIMAL")).map((change) => change.field)).toEqual(["body"]);
    expect(diffSnapshots(snapshot("DECIMAL"), snapshot("FRACTION")).map((change) => change.field)).toEqual(["body"]);
  });
});
