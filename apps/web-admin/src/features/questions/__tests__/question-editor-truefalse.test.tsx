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

const TABLE_GROUP = { name: "Score by number of correct statements" };

/** Câu 4 ý, điểm 1. Mặc định chấm theo bảng THPTQG; `perStatement` đặt cách chấm cộng điểm từng ý. */
function seedTrueFalse(perStatement = false) {
  return seedQuestion("TRUE_FALSE", (values) => {
    values.points = "1";
    values.body.statements = ["a", "b", "c", "d"].map((letter, index) => ({
      id: `s${index + 1}`,
      content: `Mệnh đề ${letter}`,
      answer: index % 2 === 0 ? "TRUE" : "FALSE",
      points: perStatement ? "0,25" : "",
    }));
    values.body.scoringMethod = perStatement ? "PER_STATEMENT" : "COUNT_TABLE";
    values.body.scoreTable = ["0,1", "0,25", "0,5", "1"];
  });
}

const statementPoints = () =>
  ["a", "b", "c", "d"].map((letter) => (screen.getByRole("textbox", { name: `Statement ${letter} points` }) as HTMLInputElement).value);

describe("trinh soan Dung/Sai hai cach cham", () => {
  it("mac dinh cham theo bang: co bang diem, khong co o diem tung y; luu giu bang", async () => {
    const user = userEvent.setup();
    const id = await seedTrueFalse();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    expect(await screen.findByRole("radio", { name: "By number of correct statements" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Points per statement" })).not.toBeChecked();
    const table = screen.getByRole("group", TABLE_GROUP);
    expect(within(table).getAllByRole("textbox").map((cell) => (cell as HTMLInputElement).value)).toEqual([
      "0,1",
      "0,25",
      "0,5",
      "1",
    ]);
    expect(screen.queryByRole("textbox", { name: /^Statement a points/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({
      scoringMethod: "COUNT_TABLE",
      scoreTable: [0.1, 0.25, 0.5, 1],
      statements: [{ points: null }, { points: null }, { points: null }, { points: null }],
    });
  });

  it("chuyen sang cong diem tung y: chia deu diem cau, tong phai khop, doi bang diem bi an", async () => {
    const user = userEvent.setup();
    const id = await seedTrueFalse();
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("radio", { name: "Points per statement" }));

    expect(screen.queryByRole("group", TABLE_GROUP)).not.toBeInTheDocument();
    expect(statementPoints()).toEqual(["0,25", "0,25", "0,25", "0,25"]);
    expect(screen.getByText("Points total: 1 / 1")).toBeInTheDocument();

    const first = screen.getByRole("textbox", { name: "Statement a points" });
    await user.clear(first);
    await user.type(first, "0,5");
    expect(screen.getByText("Points total: 1.25 / 1, does not match the question points yet")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("The statement points must add up to the question points.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Split evenly" }));
    expect(statementPoints()).toEqual(["0,25", "0,25", "0,25", "0,25"]);
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({
      scoringMethod: "PER_STATEMENT",
      scoreTable: [],
      statements: [{ points: 0.25 }, { points: 0.25 }, { points: 0.25 }, { points: 0.25 }],
    });
  });

  it("them y khi cong diem tung y thi chia deu lai ca diem y lan bang diem; doi lai cach cham giu bang dung do dai", async () => {
    const user = userEvent.setup();
    const id = await seedTrueFalse(true);
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    expect(await screen.findByRole("radio", { name: "Points per statement" })).toBeChecked();
    expect(statementPoints()).toEqual(["0,25", "0,25", "0,25", "0,25"]);

    await user.click(screen.getByRole("button", { name: "Add statement" }));
    const fifth = screen.getByRole("textbox", { name: "Statement e text" });
    expect(fifth).toHaveFocus();
    await user.type(fifth, "Mệnh đề e");
    await user.click(within(screen.getByRole("group", { name: "Answer for statement e" })).getByRole("radio", { name: "True" }));
    expect(
      ["a", "b", "c", "d", "e"].map((letter) => (screen.getByRole("textbox", { name: `Statement ${letter} points` }) as HTMLInputElement).value),
    ).toEqual(["0,2", "0,2", "0,2", "0,2", "0,2"]);
    expect(screen.getByText("Points total: 1 / 1")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "By number of correct statements" }));
    const table = screen.getByRole("group", TABLE_GROUP);
    expect(within(table).getAllByRole("textbox").map((cell) => (cell as HTMLInputElement).value)).toEqual([
      "0,2",
      "0,4",
      "0,6",
      "0,8",
      "1",
    ]);
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({
      scoringMethod: "COUNT_TABLE",
      scoreTable: [0.2, 0.4, 0.6, 0.8, 1],
      statements: [{ points: null }, { points: null }, { points: null }, { points: null }, { points: null }],
    });
  });

  it("y chua chon Dung/Sai thi chan luu khi cong diem tung y", async () => {
    const user = userEvent.setup();
    const id = await seedTrueFalse(true);
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("button", { name: "Add statement" }));
    await user.type(screen.getByRole("textbox", { name: "Statement e text" }), "Mệnh đề e");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByText("Choose True or False for this statement.")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });
});
