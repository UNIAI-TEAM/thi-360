import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QuestionEditorView } from "../components/question-editor-view";
import { emptyChild, isBlankNumberStart, toEditorValues, toSaveRequest, type EditorChild } from "../editor-form";
import { validateQuestionEditor } from "../editor-schema";
import { asWireBody, fromWireBody, toWireBody, type ChildClassificationIds } from "../wire-body";
import type { QuestionDetail } from "../types";
import { fixtureAll } from "./api-fixtures";
import { renderEn, seedQuestion, storedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

beforeEach(() => {
  navigation.push.mockReset();
});

const IDS: ChildClassificationIds = { gradeItemId: () => null, levelItemId: () => null };

function choiceChild(content: string): EditorChild {
  const child = emptyChild("SINGLE_CHOICE");
  child.content = content;
  child.body.options = child.body.options.map((option, index) => ({ ...option, content: `P${index + 1}`, correct: index === 0 }));
  return child;
}

function setDetail(blankNumberStart: number): QuestionDetail {
  const set = fixtureAll().find((question) => question.type === "QUESTION_SET");
  if (!set) throw new Error("fixture has no QUESTION_SET");
  return { ...set, body: { type: "QUESTION_SET", passage: set.content, blankNumberStart, childIds: set.children.map((child) => child.id) } };
}

describe("blankNumberStart trên dây và trong biểu mẫu (ED-VG F2)", () => {
  it("đọc từ dây; thiếu thì là 1", () => {
    expect(fromWireBody({ type: "QUESTION_SET", passage: "p", blankNumberStart: 21, children: [] })).toMatchObject({ blankNumberStart: 21 });
    expect(fromWireBody({ type: "QUESTION_SET", passage: "p", children: [] })).toMatchObject({ blankNumberStart: 1 });
  });

  it("nạp câu chùm có 21, lưu không sửa gì: thân gửi đi vẫn là 21", () => {
    const loaded = setDetail(21);
    const values = toEditorValues(loaded);
    expect(values.body.blankNumberStart).toBe("21");
    const request = toSaveRequest(values);
    expect(request.body).toMatchObject({ type: "QUESTION_SET", blankNumberStart: 21 });
    const wire = toWireBody(request.body, request.children, IDS);
    expect(wire).toMatchObject({ type: "QUESTION_SET", blankNumberStart: 21 });
    // Máy chủ trả lại đúng 21 khi đọc về.
    expect(fromWireBody(asWireBody(wire as never))).toMatchObject({ blankNumberStart: 21 });
  });

  it("chỉ nhận số nguyên 1-200", () => {
    for (const ok of ["1", "21", " 200 "]) expect(isBlankNumberStart(ok)).toBe(true);
    for (const bad of ["", "0", "201", "-1", "1,5", "2.0", "abc", "0021"]) expect(isBlankNumberStart(bad)).toBe(false);
    const values = toEditorValues(setDetail(1));
    values.body.blankNumberStart = "201";
    expect(validateQuestionEditor(values)).toContainEqual({ path: ["body", "blankNumberStart"], message: "blankNumberStartInvalid" });
    values.body.blankNumberStart = "200";
    expect(validateQuestionEditor(values).some((issue) => issue.message === "blankNumberStartInvalid")).toBe(false);
  });
});

describe("lưu câu chùm trong trình soạn (ED-VG F2)", () => {
  it("mở câu chùm có blankNumberStart 21 rồi Lưu mà không sửa: máy chủ vẫn giữ 21", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("QUESTION_SET", (values) => {
      values.content = "Ngữ liệu chung";
      values.body.blankNumberStart = "21";
      values.children = [choiceChild("Câu 1"), choiceChild("Câu 2")];
    });
    expect(storedQuestion(id).body).toMatchObject({ blankNumberStart: 21 });

    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await user.click(await screen.findByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/questions/${id}`));
    expect(storedQuestion(id).body).toMatchObject({ type: "QUESTION_SET", blankNumberStart: 21 });
  });
});
