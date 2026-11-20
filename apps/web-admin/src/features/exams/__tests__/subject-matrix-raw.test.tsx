import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useMemo, useState, useSyncExternalStore } from "react";
import { describe, expect, it } from "vitest";
import type { ContentReference } from "@/features/content/types";
import { getContentReferenceFixture } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { MatrixDistributionBlock } from "../subject-forms/matrix-distribution-block";
import type { MatrixBlockProps } from "../subject-forms/matrix-block-props";
import { MatrixStructureBlock } from "../subject-forms/matrix-structure-block";
import { createRawInputStore, type RawInputStore } from "../subject-forms/raw-input-store";
import { subjectFormFor } from "../subject-forms/registry";
import type { ExamSection } from "../types";
import { block, section } from "./subject-form-builders";

/**
 * R-15 (CR-2 round 1, R1-5, R1-9): chữ gõ sai nằm ở kho nhập thô của view và sống lâu hơn màn. Màn dựng lại (cùng kho, cùng bản nháp) phải
 * hiện đúng chữ đã gõ và lỗi tại ô, nối lỗi vào ô bằng `aria-describedby`, và hàng khối lớp của V-ACT Tiếng Việt phải hiện lại để có chỗ sửa.
 * Màn đóng rồi mở lại chỉ tới được bằng đường điều hướng không qua hộp thoại chuyển mục (bấm lỗi chặn lưu của môn khác), nên dựng khối thẳng
 * với một kho thật và nút ẩn/hiện thay cho đường đó.
 */
const reference = await getContentReferenceFixture();
const axes = { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((difficulty) => difficulty.code) };
const nameOf = (items: ContentReference["grades"], code: string) => items.find((item) => item.code === code)?.name ?? code;
const labels = { grade: (code: string) => nameOf(reference.grades, code), difficulty: (code: string) => nameOf(reference.difficulties, code) };

function Host({ store, initial, kindCode, subjectCode }: { store: RawInputStore; initial: ExamSection; kindCode: string; subjectCode: string }) {
  const [current, setCurrent] = useState(initial);
  const [shown, setShown] = useState(true);
  const entries = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  const raw = useMemo<MatrixBlockProps["raw"]>(
    () => ({
      get: (field) => {
        const found = entries.find((item) => item.sectionId === current.id && item.blockId === null && item.field === field);
        return found ? { text: found.text, code: found.code } : null;
      },
      hasPrefix: (prefix) => entries.some((item) => item.sectionId === current.id && item.field.startsWith(prefix)),
      set: (field, text, code) => store.set({ sectionId: current.id, blockId: null, field, text, code }),
      clear: (field) => store.clear(current.id, null, field),
    }),
    [entries, store, current.id],
  );
  const entry = subjectFormFor(kindCode, subjectCode)!;
  const props: MatrixBlockProps = {
    section: current,
    entry,
    kindCode,
    subjectName: subjectCode,
    total: current.questionCount,
    axes,
    labels,
    reference,
    onChange: (update) => setCurrent(update),
    templateLocked: false,
    raw,
  };
  return (
    <>
      <button type="button" onClick={() => setShown((value) => !value)}>
        đóng mở màn
      </button>
      {shown && (
        <>
          <MatrixStructureBlock {...props} />
          <MatrixDistributionBlock {...props} />
        </>
      )}
    </>
  );
}

const ly = () =>
  section("sec-ly", "LY", [block("ly-single", { questionCount: 21 }), block("ly-set", { questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3 }), block("ly-fill", { questionType: "SHORT_ANSWER", questionCount: 1 })]);
const dimension = (title: "Khối lớp" | "Độ khó") => within(screen.getByRole("heading", { level: 4, name: title }).closest("[data-form-field]") as HTMLElement);
const reopen = async (user: UserEvent) => {
  await user.click(screen.getByRole("button", { name: "đóng mở màn" }));
  expect(screen.queryByRole("heading", { level: 3, name: "Cấu trúc câu" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "đóng mở màn" }));
  await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" });
};

describe("MT: ô mở lại đọc nhập thô (R-15, R1-5, R1-9)", () => {
  it("ô độ khó: chữ sai và lỗi còn sau khi màn đóng rồi mở; lỗi nối vào ô bằng aria-describedby; gõ số đúng gỡ lỗi và gỡ mục nhập thô", async () => {
    const user = userEvent.setup();
    const store = createRawInputStore();
    renderWithProviders(<Host store={store} initial={ly()} kindCode="QDA" subjectCode="LY" />);
    await user.type(dimension("Độ khó").getByLabelText("Nhận biết"), "abc");
    expect(store.get("sec-ly", null, "DIFFICULTY:RECOGNITION")).toMatchObject({ text: "abc", code: "invalidNumber" });

    await reopen(user);
    const cell = dimension("Độ khó").getByLabelText("Nhận biết");
    expect(cell).toHaveValue("abc");
    expect(cell).toHaveAttribute("aria-invalid", "true");
    const alert = within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement).getByRole("alert");
    expect(alert).toHaveTextContent("Nhập một số không âm.");
    expect(cell).toHaveAttribute("aria-describedby", alert.id);

    await user.clear(cell);
    await user.type(cell, "5");
    await waitFor(() => expect(within(screen.getByRole("heading", { level: 4, name: "Độ khó" }).closest("[data-form-field]") as HTMLElement).queryByRole("alert")).not.toBeInTheDocument());
    expect(store.hasRawInputs()).toBe(false);
  });

  it("cột Tỷ lệ của V-ACT: chữ sai nằm lại ở cột Tỷ lệ (không chuyển sang cột Số câu) và nối lỗi vào cả hai ô", async () => {
    const user = userEvent.setup();
    const store = createRawInputStore();
    const toan = section("sec-toan", "TOAN", [block("t-single", { questionCount: 12 }), block("t-set", { questionType: "QUESTION_SET", setCount: null, setSize: null, questionCount: 18 })], { questionCount: 30 });
    renderWithProviders(<Host store={store} initial={toan} kindCode="VACT" subjectCode="TOAN" />);
    await user.type(dimension("Độ khó").getByRole("textbox", { name: "Tỷ lệ của Nhận biết" }), "x1");
    expect(store.get("sec-toan", null, "DIFFICULTY:RECOGNITION")).toMatchObject({ text: "x1", code: "invalidPercent" });

    await reopen(user);
    const percent = dimension("Độ khó").getByRole("textbox", { name: "Tỷ lệ của Nhận biết" });
    expect(percent).toHaveValue("x1");
    expect(dimension("Độ khó").getByLabelText("Nhận biết")).toHaveValue("");
    const alert = dimension("Độ khó").getByRole("alert");
    expect(percent).toHaveAttribute("aria-describedby", alert.id);
    expect(dimension("Độ khó").getByLabelText("Nhận biết")).toHaveAttribute("aria-describedby", alert.id);
  });

  it("hàng khối lớp của V-ACT Tiếng Việt hiện lại khi còn chữ sai ở một ô lớp, kèm lỗi để sửa", async () => {
    const store = createRawInputStore();
    store.set({ sectionId: "sec-vn", blockId: null, field: "GRADE:GRADE_12", text: "abc", code: "invalidNumber" });
    const vn = section("sec-vn", "VIETNAMESE", [block("vn-single", { questionCount: 30 })], { questionCount: 30 });
    renderWithProviders(<Host store={store} initial={vn} kindCode="VACT" subjectCode="VIETNAMESE" />);
    expect(screen.getByRole("heading", { level: 4, name: "Khối lớp" })).toBeInTheDocument();
    expect(dimension("Khối lớp").getByLabelText("Lớp 12")).toHaveValue("abc");
    expect(dimension("Khối lớp").getByRole("alert")).toBeInTheDocument();
    // Không có chữ sai thì hàng vẫn ẩn như R-4 yêu cầu.
    store.clearAll();
    await waitFor(() => expect(screen.queryByRole("heading", { level: 4, name: "Khối lớp" })).not.toBeInTheDocument());
  });

  it("ô Cấu trúc câu: chữ sai và lỗi còn sau khi màn đóng rồi mở", async () => {
    const user = userEvent.setup();
    const store = createRawInputStore();
    renderWithProviders(<Host store={store} initial={ly()} kindCode="QDA" subjectCode="LY" />);
    const fill = within(screen.getByRole("group", { name: "Điền đáp án" })).getByRole("textbox");
    // Gõ nối vào số đang có: ô giữ 1 trong bản nháp, chữ "1x" là nhập thô.
    await user.type(fill, "x");
    expect(store.get("sec-ly", null, "FILL")).toMatchObject({ text: "1x", code: "invalidNumber" });

    await reopen(user);
    const cell = within(screen.getByRole("group", { name: "Điền đáp án" }));
    expect(cell.getByRole("textbox")).toHaveValue("1x");
    expect(cell.getByRole("alert")).toHaveTextContent("Nhập một số không âm.");
  });
});
